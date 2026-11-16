import FirebaseStorage
import Foundation
import ProHostShared
import UIKit

/// Cloud Storage for the shared Kotlin `BridgedStorageGateway` (same bucket as Android). Paths, content types and what is
/// returned (download URL or private gs:// reference) are decided in shared code.
final class FirebaseStorageBridge: NSObject, IosStorageNative {
    private let storage = Storage.storage(url: "gs://prohost-f766f.firebasestorage.app")

    func uploadData(path: String, data: Data, contentType: String?, progress: ProgressCallback, completion: UploadCallback) {
        let ref = storage.reference(withPath: path)
        let task = ref.putData(data, metadata: Self.metadata(contentType)) { _, error in Self.finish(ref, error, completion) }
        Self.observe(task, progress)
    }

    func uploadFileUrl(path: String, fileUrl: String, contentType: String?, progress: ProgressCallback, completion: UploadCallback) {
        guard let url = URL(string: fileUrl) else { return completion.done(downloadUrl: nil, gsUri: nil, error: "bad file URL") }
        let ref = storage.reference(withPath: path)
        let task = ref.putFile(from: url, metadata: Self.metadata(contentType)) { _, error in Self.finish(ref, error, completion) }
        Self.observe(task, progress)
    }

    /// Android's rule: longest side at most maxDim px, JPEG at the given quality (0-100).
    func compressJpeg(data: Data, maxDim: Int32, quality: Int32) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let longest = max(image.size.width, image.size.height) * image.scale
        var output = image
        if longest > CGFloat(maxDim) {
            let factor = CGFloat(maxDim) / longest
            let size = CGSize(width: (image.size.width * image.scale * factor).rounded(.down),
                              height: (image.size.height * image.scale * factor).rounded(.down))
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            output = UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        }
        return output.jpegData(compressionQuality: CGFloat(quality) / 100)
    }

    func deletePrefix(path: String, completion: BoolCallback) {
        Self.deleteRecursively(storage.reference(withPath: path)) { ok in completion.done(value: ok) }
    }

    private static func deleteRecursively(_ ref: StorageReference, done: @escaping (Bool) -> Void) {
        ref.listAll { result, error in
            guard error == nil, let result else { return done(false) }
            let group = DispatchGroup()
            for item in result.items { group.enter(); item.delete { _ in group.leave() } }
            for prefix in result.prefixes { group.enter(); deleteRecursively(prefix) { _ in group.leave() } }
            group.notify(queue: .main) { done(true) }
        }
    }

    private static func metadata(_ contentType: String?) -> StorageMetadata? {
        guard let contentType else { return nil }
        let m = StorageMetadata()
        m.contentType = contentType
        return m
    }

    private static func observe(_ task: StorageUploadTask, _ progress: ProgressCallback) {
        task.observe(.progress) { snapshot in
            let fraction = snapshot.progress.map { $0.totalUnitCount > 0 ? Double($0.completedUnitCount) / Double($0.totalUnitCount) : 0 } ?? 0
            progress.progress(fraction: fraction)
        }
    }

    private static func finish(_ ref: StorageReference, _ error: Error?, _ completion: UploadCallback) {
        if let error { return completion.done(downloadUrl: nil, gsUri: nil, error: error.localizedDescription) }
        let gs = "gs://\(ref.bucket)/\(ref.fullPath)"
        ref.downloadURL { url, error in
            completion.done(downloadUrl: url?.absoluteString, gsUri: gs, error: url == nil ? (error?.localizedDescription ?? "no download URL") : nil)
        }
    }
}
