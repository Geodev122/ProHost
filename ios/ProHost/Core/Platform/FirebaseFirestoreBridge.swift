import FirebaseFirestore
import Foundation
import ProHostShared

/// Firestore for the shared Kotlin `BridgedFirestoreGateway`, which holds every query and write (identical to Android).
/// Values handed to Kotlin: Timestamps as epoch millis, numbers as Kotlin Long/Double/Boolean boxes, missing/null fields dropped.
final class FirebaseFirestoreBridge: NSObject, FirestoreBridge {
    private let db = Firestore.firestore()

    func listenQuery(
        collectionPath: String, filters: [QueryFilter], orderByField: String?, descending: Bool, limit: Int32,
        onSnapshot: @escaping ([DocSnapshot]) -> Void, onError: @escaping (String) -> Void
    ) -> ListenerHandle {
        let registration = query(collectionPath, filters, orderByField, descending, limit).addSnapshotListener { snapshot, error in
            if let error { return onError(error.localizedDescription) }
            onSnapshot(snapshot?.documents.map(Self.doc) ?? [])
        }
        return FirestoreListener(registration)
    }

    func listenDocument(docPath: String, onSnapshot: @escaping (DocSnapshot?) -> Void, onError: @escaping (String) -> Void) -> ListenerHandle {
        let registration = db.document(docPath).addSnapshotListener { snapshot, error in
            if let error { return onError(error.localizedDescription) }
            onSnapshot(snapshot.flatMap(Self.docIfExists))
        }
        return FirestoreListener(registration)
    }

    func queryOnce(
        collectionPath: String, filters: [QueryFilter], orderByField: String?, descending: Bool, limit: Int32,
        completion: @escaping ([DocSnapshot]?, String?) -> Void
    ) {
        query(collectionPath, filters, orderByField, descending, limit).getDocuments { snapshot, error in
            if let error { return completion(nil, error.localizedDescription) }
            completion(snapshot?.documents.map(Self.doc) ?? [], nil)
        }
    }

    func getDocument(docPath: String, completion: @escaping (DocSnapshot?, String?) -> Void) {
        db.document(docPath).getDocument { snapshot, error in
            if let error { return completion(nil, error.localizedDescription) }
            completion(snapshot.flatMap(Self.docIfExists), nil)
        }
    }

    func setDocument(docPath: String, data: [String: Any], merge: Bool, completion: @escaping (String?) -> Void) {
        db.document(docPath).setData(Self.toFirestore(data), merge: merge) { completion($0?.localizedDescription) }
    }

    func updateDocument(docPath: String, data: [String: Any], completion: @escaping (String?) -> Void) {
        db.document(docPath).updateData(Self.toFirestore(data)) { completion($0?.localizedDescription) }
    }

    func deleteDocument(docPath: String, completion: @escaping (String?) -> Void) {
        db.document(docPath).delete { completion($0?.localizedDescription) }
    }

    func batchUpdate(docPaths: [String], data: [String: Any], completion: @escaping (String?) -> Void) {
        let batch = db.batch()
        let fields = Self.toFirestore(data)
        docPaths.forEach { batch.updateData(fields, forDocument: db.document($0)) }
        batch.commit { completion($0?.localizedDescription) }
    }

    // MARK: - Queries

    private func query(_ path: String, _ filters: [QueryFilter], _ orderBy: String?, _ descending: Bool, _ limit: Int32) -> Query {
        var q: Query = db.collection(path)
        for f in filters {
            let value = Self.toFirestore(f.value as Any)
            switch f.op {
            case ">": q = q.whereField(f.field, isGreaterThan: value)
            default: q = q.whereField(f.field, isEqualTo: value)
            }
        }
        if let orderBy { q = q.order(by: orderBy, descending: descending) }
        if limit > 0 { q = q.limit(to: Int(limit)) }
        return q
    }

    // MARK: - Firestore -> Kotlin

    private static func doc(_ snapshot: QueryDocumentSnapshot) -> DocSnapshot {
        DocSnapshot(id: snapshot.documentID, data: toKotlinMap(snapshot.data()))
    }

    private static func docIfExists(_ snapshot: DocumentSnapshot) -> DocSnapshot? {
        guard snapshot.exists, let data = snapshot.data() else { return nil }
        return DocSnapshot(id: snapshot.documentID, data: toKotlinMap(data))
    }

    private static func toKotlinMap(_ data: [String: Any]) -> [String: Any] {
        var out: [String: Any] = [:]
        for (key, value) in data { if let v = toKotlin(value) { out[key] = v } }
        return out
    }

    private static func toKotlin(_ value: Any) -> Any? {
        switch value {
        case is NSNull: return nil
        case let ts as Timestamp: return KotlinValues.shared.long(v: Int64(ts.seconds) * 1000 + Int64(ts.nanoseconds / 1_000_000))
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return KotlinValues.shared.bool(v: n.boolValue) }
            let type = String(cString: n.objCType)
            return (type == "d" || type == "f") ? KotlinValues.shared.double(v: n.doubleValue) : KotlinValues.shared.long(v: n.int64Value)
        case let s as String: return s
        case let a as [Any]: return a.compactMap(toKotlin)
        case let d as [String: Any]: return toKotlinMap(d)
        case let g as GeoPoint: return ["latitude": KotlinValues.shared.double(v: g.latitude), "longitude": KotlinValues.shared.double(v: g.longitude)]
        default: return String(describing: value)
        }
    }

    // MARK: - Kotlin -> Firestore

    private static func toFirestore(_ data: [String: Any]) -> [String: Any] {
        data.mapValues { toFirestore($0) }
    }

    private static func toFirestore(_ value: Any) -> Any {
        switch value {
        case let op as FieldOp.Increment: return FieldValue.increment(op.by)
        case let op as FieldOp.ArrayUnion: return FieldValue.arrayUnion(op.values.map { toFirestore($0) })
        case let op as FieldOp.ArrayRemove: return FieldValue.arrayRemove(op.values.map { toFirestore($0) })
        case let a as [Any]: return a.map { toFirestore($0) }
        case let d as [String: Any]: return toFirestore(d)
        default: return value
        }
    }
}

private final class FirestoreListener: NSObject, ListenerHandle {
    private let registration: ListenerRegistration
    init(_ registration: ListenerRegistration) { self.registration = registration }
    func remove() { registration.remove() }
}
