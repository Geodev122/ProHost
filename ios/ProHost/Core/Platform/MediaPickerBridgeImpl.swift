import PhotosUI
import ProHostShared
import UIKit
import UniformTypeIdentifiers

/// The system pickers for the shared screens: PHPicker (no photo-library permission needed), UIDocumentPicker and the
/// camera. Photos become JPEG copies in the temporary folder; completions run on the main thread.
final class MediaPickerBridgeImpl: NSObject, MediaPickerBridge {
    private var active: NSObject?

    func pickPhotos(limit: Int32, completion: @escaping ([String]) -> Void) {
        DispatchQueue.main.async {
            var config = PHPickerConfiguration()
            config.filter = .images
            config.selectionLimit = Int(limit)
            let picker = PHPickerViewController(configuration: config)
            let delegate = PhotoDelegate { [weak self] urls in
                self?.active = nil
                completion(urls)
            }
            picker.delegate = delegate
            self.active = delegate
            guard Self.present(picker) else { self.active = nil; return completion([]) }
        }
    }

    func pickDocumentFile(completion: @escaping (String?, String?) -> Void) {
        DispatchQueue.main.async {
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.pdf, .image], asCopy: true)
            let delegate = DocumentDelegate { [weak self] url in
                self?.active = nil
                completion(url?.absoluteString, url?.lastPathComponent)
            }
            picker.delegate = delegate
            self.active = delegate
            guard Self.present(picker) else { self.active = nil; return completion(nil, nil) }
        }
    }

    func capturePhoto(completion: @escaping (String?) -> Void) {
        DispatchQueue.main.async {
            guard UIImagePickerController.isSourceTypeAvailable(.camera) else { return completion(nil) }
            let picker = UIImagePickerController()
            picker.sourceType = .camera
            let delegate = CameraDelegate { [weak self] url in
                self?.active = nil
                completion(url)
            }
            picker.delegate = delegate
            self.active = delegate
            guard Self.present(picker) else { self.active = nil; return completion(nil) }
        }
    }

    fileprivate static func writeJPEG(_ image: UIImage) -> String? {
        guard let data = image.jpegData(compressionQuality: 0.9) else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("picked-\(UUID().uuidString).jpg")
        do { try data.write(to: url) } catch { return nil }
        return url.absoluteString
    }

    private static func present(_ controller: UIViewController) -> Bool {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        guard var top = window?.rootViewController else { return false }
        while let presented = top.presentedViewController { top = presented }
        controller.popoverPresentationController?.sourceView = top.view
        top.present(controller, animated: true)
        return true
    }
}

private final class PhotoDelegate: NSObject, PHPickerViewControllerDelegate {
    private let done: ([String]) -> Void
    init(done: @escaping ([String]) -> Void) { self.done = done }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard !results.isEmpty else { return done([]) }
        var urls = [String?](repeating: nil, count: results.count)
        let group = DispatchGroup()
        for (index, result) in results.enumerated() where result.itemProvider.canLoadObject(ofClass: UIImage.self) {
            group.enter()
            result.itemProvider.loadObject(ofClass: UIImage.self) { object, _ in
                if let image = object as? UIImage { urls[index] = MediaPickerBridgeImpl.writeJPEG(image) }
                group.leave()
            }
        }
        group.notify(queue: .main) { self.done(urls.compactMap { $0 }) }
    }
}

private final class DocumentDelegate: NSObject, UIDocumentPickerDelegate {
    private let done: (URL?) -> Void
    init(done: @escaping (URL?) -> Void) { self.done = done }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { done(urls.first) }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { done(nil) }
}

private final class CameraDelegate: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    private let done: (String?) -> Void
    init(done: @escaping (String?) -> Void) { self.done = done }

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        done((info[.originalImage] as? UIImage).flatMap { MediaPickerBridgeImpl.writeJPEG($0) })
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        done(nil)
    }
}
