import EventKit
import EventKitUI
import ProHostShared
import UIKit

/// "Add payment reminders": Apple's new-event sheet, prefilled (all-day, optional monthly repeat). The person picks the
/// calendar and saves; EventKitUI needs no calendar permission for this. The Android counterpart is the insert intent.
final class CalendarBridgeImpl: NSObject, CalendarBridge, EKEventEditViewDelegate {
    private let store = EKEventStore()

    func presentAllDayEvent(title: String, notes: String, startUtcMillis: Int64, monthlyCount: Int32) {
        DispatchQueue.main.async {
            let event = EKEvent(eventStore: self.store)
            event.title = title
            event.notes = notes
            event.isAllDay = true
            // The UTC calendar date of the due day, as a local all-day date (Android: all-day events start at UTC midnight).
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(identifier: "UTC")!
            let day = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: TimeInterval(startUtcMillis) / 1000))
            let start = Calendar.current.date(from: day) ?? Date()
            event.startDate = start
            event.endDate = start
            if monthlyCount > 1 {
                event.addRecurrenceRule(EKRecurrenceRule(
                    recurrenceWith: .monthly, interval: 1, end: EKRecurrenceEnd(occurrenceCount: Int(monthlyCount))))
            }
            let editor = EKEventEditViewController()
            editor.eventStore = self.store
            editor.event = event
            editor.editViewDelegate = self
            Self.present(editor)
        }
    }

    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        controller.dismiss(animated: true)
    }

    private static func present(_ controller: UIViewController) {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        guard var top = window?.rootViewController else { return }
        while let presented = top.presentedViewController { top = presented }
        top.present(controller, animated: true)
    }
}
