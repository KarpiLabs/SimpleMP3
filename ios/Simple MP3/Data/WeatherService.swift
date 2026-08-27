//
//  WeatherService.swift
//  Simple MP3
//

import Foundation
import CoreLocation

@MainActor
final class WeatherService: NSObject, CLLocationManagerDelegate {
    static let shared = WeatherService()

    private let locationManager = CLLocationManager()
    private var locationContinuation: CheckedContinuation<CLLocation?, Never>?
    private var authContinuation: CheckedContinuation<Bool, Never>?
    private var cachedWeather: (text: String, timestamp: Date)?

    override private init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    /// Fetches a short weather summary string (e.g. "72°F Sunny" or "22°C Clear").
    /// Returns cached result if fetched within the last 15 minutes.
    func getWeatherSummary() async -> String? {
        if let cached = cachedWeather, Date().timeIntervalSince(cached.timestamp) < 900 {
            return cached.text
        }

        guard let location = await requestLocation() else {
            return nil
        }

        guard let summary = await fetchOpenMeteoWeather(lat: location.coordinate.latitude, lon: location.coordinate.longitude) else {
            return nil
        }

        cachedWeather = (summary, Date())
        return summary
    }

    private func requestLocation() async -> CLLocation? {
        var status = locationManager.authorizationStatus

        if status == .notDetermined {
            let granted = await requestAuthorization()
            if !granted { return nil }
            status = locationManager.authorizationStatus
        }

        if status == .denied || status == .restricted {
            return nil
        }

        if let loc = locationManager.location, Date().timeIntervalSince(loc.timestamp) < 600 {
            return loc
        }

        return await withCheckedContinuation { continuation in
            if self.locationContinuation != nil {
                self.locationContinuation?.resume(returning: nil)
            }
            self.locationContinuation = continuation
            self.locationManager.requestLocation()
        }
    }

    private func requestAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            if self.authContinuation != nil {
                self.authContinuation?.resume(returning: false)
            }
            self.authContinuation = continuation
            self.locationManager.requestWhenInUseAuthorization()
        }
    }

    // MARK: - CLLocationManagerDelegate

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        guard status != .notDetermined else { return }
        Task { @MainActor in
            if let continuation = self.authContinuation {
                let granted = (status == .authorizedWhenInUse || status == .authorizedAlways)
                continuation.resume(returning: granted)
                self.authContinuation = nil
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let loc = locations.first
        Task { @MainActor in
            self.locationContinuation?.resume(returning: loc)
            self.locationContinuation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            self.locationContinuation?.resume(returning: nil)
            self.locationContinuation = nil
        }
    }

    // MARK: - API Fetch

    private func fetchOpenMeteoWeather(lat: Double, lon: Double) async -> String? {
        let usesFahrenheit = Locale.current.measurementSystem == .us || Locale.current.region?.identifier == "US"
        let tempUnit = usesFahrenheit ? "fahrenheit" : "celsius"
        let urlString = "https://api.open-meteo.com/v1/forecast?latitude=\(lat)&longitude=\(lon)&current_weather=true&temperature_unit=\(tempUnit)"

        guard let url = URL(string: urlString) else { return nil }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }

            let decoded = try JSONDecoder().decode(OpenMeteoResponse.self, from: data)
            let current = decoded.current_weather
            let tempInt = Int(round(current.temperature))
            let unitSymbol = usesFahrenheit ? "°F" : "°C"
            let condition = weatherCodeToCondition(current.weathercode)

            return "\(tempInt)\(unitSymbol) \(condition)"
        } catch {
            return nil
        }
    }

    private func weatherCodeToCondition(_ code: Int) -> String {
        switch code {
        case 0: return "Clear"
        case 1, 2: return "Partly Cloudy"
        case 3: return "Overcast"
        case 45, 48: return "Foggy"
        case 51, 53, 55, 56, 57: return "Drizzle"
        case 61, 63, 65, 66, 67, 80, 81, 82: return "Rain"
        case 71, 73, 75, 77, 85, 86: return "Snow"
        case 95, 96, 99: return "Thunderstorm"
        default: return "Clear"
        }
    }
}

private struct OpenMeteoResponse: Decodable {
    struct CurrentWeather: Decodable {
        let temperature: Double
        let weathercode: Int
    }
    let current_weather: CurrentWeather
}
