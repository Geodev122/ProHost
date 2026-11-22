package com.example.ios

/**
 * The system pickers on iOS (Swift `MediaPickerBridgeImpl`): results are `file://` URLs of temporary copies (photos as JPEG),
 * read by `IosFileAccess`. Every completion runs on the main thread; a cancel completes with an empty list / null.
 */
interface MediaPickerBridge {
    /** PHPicker, images only; [limit] 0 = no limit. */
    fun pickPhotos(limit: Int, completion: (List<String>) -> Unit)
    /** UIDocumentPicker for a PDF or an image: the copy's URL and its file name. */
    fun pickDocumentFile(completion: (url: String?, name: String?) -> Unit)
    /** The camera (UIImagePickerController); null when cancelled or no camera. */
    fun capturePhoto(completion: (String?) -> Unit)
}
