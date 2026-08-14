//
//  Simple_MP3UITestsLaunchTests.swift
//  Simple MP3UITests
//
//  Created by Zach Karpinski on 8/3/26.
//

import XCTest

final class Simple_MP3UITestsLaunchTests: XCTestCase {

    override class var runsForEachTargetApplicationUIConfiguration: Bool {
        true
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLaunch() throws {
        let app = XCUIApplication()

        // Dismiss the system media library permission alert so it can't stall launch.
        addUIInterruptionMonitor(withDescription: "System Dialog") { alert in
            let allow = alert.buttons["Allow"]
            if allow.exists {
                allow.tap()
                return true
            }
            let ok = alert.buttons["OK"]
            if ok.exists {
                ok.tap()
                return true
            }
            return false
        }

        app.launch()
        app.tap()

        // Insert steps here to perform after app launch but before taking a screenshot,
        // such as logging into a test account or navigating somewhere in the app

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Launch Screen"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
