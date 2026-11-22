import GoogleMaps
import ProHostShared
import UIKit

/// Creates Google Maps views for the shared `PlatformMap` (installed only when a Maps SDK key is configured).
final class GoogleMapFactory: NSObject, NativeMapFactory {
    func createMapView(latitude: Double, longitude: Double, zoom: Float, events: NativeMapEvents) -> NativeMapView {
        GoogleMapViewBridge(latitude: latitude, longitude: longitude, zoom: zoom, events: events)
    }
}

/// One GMSMapView with the Android map's settings (normal map, no zoom controls / my-location button, compass on).
/// Markers are kept by id so a recomposition only moves or restyles what changed.
final class GoogleMapViewBridge: NSObject, NativeMapView, GMSMapViewDelegate {
    private let mapView: GMSMapView
    private let events: NativeMapEvents
    private var markers: [String: GMSMarker] = [:]
    private var styles: [String: String] = [:]
    private var loaded = false

    init(latitude: Double, longitude: Double, zoom: Float, events: NativeMapEvents) {
        self.events = events
        let options = GMSMapViewOptions()
        options.camera = GMSCameraPosition(latitude: latitude, longitude: longitude, zoom: zoom)
        mapView = GMSMapView(options: options)
        super.init()
        mapView.mapType = .normal
        mapView.settings.compassButton = true
        mapView.settings.myLocationButton = false
        mapView.delegate = self
    }

    func mapUIView() -> UIView { mapView }

    func mapMoveCamera(latitude: Double, longitude: Double, zoom: Float, animated: Bool) {
        let camera = GMSCameraPosition(latitude: latitude, longitude: longitude, zoom: zoom)
        if animated { mapView.animate(to: camera) } else { mapView.camera = camera }
    }

    func mapSetPadding(top: Double, left: Double, bottom: Double, right: Double) {
        mapView.padding = UIEdgeInsets(top: top, left: left, bottom: bottom, right: right)
    }

    func mapSetMyLocationEnabled(enabled: Bool) { mapView.isMyLocationEnabled = enabled }

    // Android's "open in Google Maps" toolbar has no iOS counterpart in the Maps SDK.
    func mapSetToolbarEnabled(enabled: Bool) {}

    func mapSetMarkers(markers specs: [NativeMapMarker]) {
        var seen = Set<String>()
        for spec in specs {
            seen.insert(spec.id)
            let marker = markers[spec.id] ?? GMSMarker()
            marker.position = CLLocationCoordinate2D(latitude: spec.latitude, longitude: spec.longitude)
            marker.title = spec.title
            marker.snippet = spec.snippet
            marker.groundAnchor = CGPoint(x: CGFloat(spec.anchorX), y: CGFloat(spec.anchorY))
            marker.zIndex = Int32(spec.zIndex)
            marker.userData = spec.id
            let style = "\(spec.kind)|\(spec.topArgb)|\(spec.baseArgb)|\(spec.selected)|\(spec.label)"
            if styles[spec.id] != style {
                marker.icon = spec.kind == 1
                    ? MarkerImages.cluster(count: Int(spec.label), hasSelected: spec.selected)
                    : MarkerImages.pin(top: spec.topArgb, base: spec.baseArgb, selected: spec.selected)
                styles[spec.id] = style
            }
            if marker.map == nil { marker.map = mapView }
            markers[spec.id] = marker
        }
        for (id, marker) in markers where !seen.contains(id) {
            marker.map = nil
            markers[id] = nil
            styles[id] = nil
        }
    }

    // MARK: GMSMapViewDelegate

    func mapView(_ mapView: GMSMapView, willMove gesture: Bool) { events.mapWillMove(byGesture: gesture) }

    func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
        events.mapCameraChanged(camera: snapshot(position))
    }

    func mapView(_ mapView: GMSMapView, idleAt position: GMSCameraPosition) {
        events.mapCameraIdle(camera: snapshot(position))
    }

    func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
        guard let id = marker.userData as? String else { return false }
        return events.mapMarkerTapped(markerId: id)
    }

    func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) { events.mapTappedOutsideMarkers() }

    func mapViewDidFinishTileRendering(_ mapView: GMSMapView) {
        guard !loaded else { return }
        loaded = true
        events.mapFinishedLoading()
    }

    private func snapshot(_ position: GMSCameraPosition) -> NativeMapCamera {
        let region = GMSCoordinateBounds(region: mapView.projection.visibleRegion())
        return NativeMapCamera(
            latitude: position.target.latitude, longitude: position.target.longitude, zoom: position.zoom,
            southLatitude: region.southWest.latitude, westLongitude: region.southWest.longitude,
            northLatitude: region.northEast.latitude, eastLongitude: region.northEast.longitude)
    }
}

/// The Android marker bitmaps (`PlatformMap.android.kt`) redrawn in points: same 200×300 teardrop geometry, gradient,
/// gloss and centre dot; the 52 pt count bubble in Steel Blue (gold when it holds the selection).
enum MarkerImages {
    private static var cache: [String: UIImage] = [:]

    static func pin(top: Int32, base: Int32, selected: Bool) -> UIImage {
        let key = "p\(top)|\(base)|\(selected)"
        if let hit = cache[key] { return hit }
        let s: CGFloat = selected ? 1.25 : 1.0
        let size = CGSize(width: 36 * s, height: 54 * s)
        let image = UIGraphicsImageRenderer(size: size).image { ctx in
            let g = ctx.cgContext
            g.scaleBy(x: size.width / 200, y: size.height / 300)
            // Ground shadow
            g.saveGState()
            g.setShadow(offset: .zero, blur: 10, color: UIColor(white: 0, alpha: 55 / 255).cgColor)
            g.setFillColor(UIColor(white: 0, alpha: 55 / 255).cgColor)
            g.fillEllipse(in: CGRect(x: 65, y: 248, width: 70, height: 17))
            g.restoreGState()
            // Teardrop: arc over the top half of the 160×160 circle at (20, 15), then down to the tip
            let path = UIBezierPath()
            path.move(to: CGPoint(x: 30.72, y: 135))
            path.addArc(withCenter: CGPoint(x: 100, y: 95), radius: 80, startAngle: .pi, endAngle: 0, clockwise: true)
            path.addLine(to: CGPoint(x: 100, y: 255))
            path.close()
            g.saveGState()
            g.addPath(path.cgPath)
            g.clip()
            let colors = [color(top).cgColor, color(base).cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                g.drawLinearGradient(gradient, start: CGPoint(x: 100, y: 15), end: CGPoint(x: 100, y: 255), options: [])
            }
            // Upper-left gloss (white → transparent, alpha 130/255)
            let gloss = [UIColor(white: 1, alpha: 130 / 255).cgColor, UIColor(white: 1, alpha: 0).cgColor] as CFArray
            if let radial = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: gloss, locations: [0, 1]) {
                g.drawRadialGradient(radial, startCenter: CGPoint(x: 76, y: 70), startRadius: 0,
                                     endCenter: CGPoint(x: 76, y: 70), endRadius: 45, options: [])
            }
            g.restoreGState()
            // White centre circle
            g.setFillColor(UIColor.white.cgColor)
            g.fillEllipse(in: CGRect(x: 70, y: 65, width: 60, height: 60))
        }
        cache[key] = image
        return image
    }

    static func cluster(count: Int, hasSelected: Bool) -> UIImage {
        let key = "c\(count)|\(hasSelected)"
        if let hit = cache[key] { return hit }
        let side: CGFloat = 52
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { ctx in
            let g = ctx.cgContext
            let radius = side / 2 - 4
            let circle = CGRect(x: side / 2 - radius, y: side / 2 - radius, width: radius * 2, height: radius * 2)
            let fill = hasSelected ? UIColor(red: 1, green: 0xB3 / 255, blue: 0, alpha: 1)
                : UIColor(red: 0x2B / 255, green: 0x5A / 255, blue: 0x8C / 255, alpha: 1)
            g.setFillColor(fill.cgColor)
            g.fillEllipse(in: circle)
            g.setStrokeColor(UIColor.white.cgColor)
            g.setLineWidth(3)
            g.strokeEllipse(in: circle)
            let text = count > 99 ? "99+" : String(count)
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 15), .foregroundColor: UIColor.white
            ]
            let textSize = (text as NSString).size(withAttributes: attributes)
            (text as NSString).draw(
                at: CGPoint(x: (side - textSize.width) / 2, y: (side - textSize.height) / 2), withAttributes: attributes)
        }
        cache[key] = image
        return image
    }

    private static func color(_ argb: Int32) -> UIColor {
        let v = UInt32(bitPattern: argb)
        return UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
                       blue: CGFloat(v & 0xFF) / 255, alpha: CGFloat((v >> 24) & 0xFF) / 255)
    }
}
