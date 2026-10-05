package com.example.data.repository

import com.example.data.model.*

/**
 * The built-in space-architecture catalogue (space types, division types, facilities,
 * amenities, rental strategies, attendee packages) used until an admin saves their own,
 * and by "Add missing defaults" / "Reset to defaults" in Admin › Schema.
 */
internal fun createDefaultSchema(): SpaceArchitectureSchema {
    return SpaceArchitectureSchema(
        spaceTypes = listOf(
            SchemaItem("ST-01", "Private Office", "Dedicated self-contained lockable office suites", SchemaCategory.SPACE_TYPE, "Apartment"),
            SchemaItem("ST-02", "Center", "Multi-disciplinary center / medical polyclinic compound", SchemaCategory.SPACE_TYPE, "Business"),
            SchemaItem("ST-03", "Polyclinic", "Certified medical examination rooms & clinical facilities", SchemaCategory.SPACE_TYPE, "LocalHospital"),
            SchemaItem("ST-04", "Co-working Space", "Open collaborative desks and flexible shared work hubs", SchemaCategory.SPACE_TYPE, "Groups"),
            SchemaItem("ST-05", "Executive Boardroom", "High-profile executive meeting and conference suites", SchemaCategory.SPACE_TYPE, "MeetingRoom"),
            SchemaItem("ST-06", "Consultation Suite", "Acoustically isolated private consultation rooms", SchemaCategory.SPACE_TYPE, "Psychology"),
            SchemaItem(
                "ST-07",
                "Hotel & Serviced Apartments",
                "Hotel property with suites, meeting rooms and event halls for rent",
                SchemaCategory.SPACE_TYPE,
                "Hotel"
            ),
            SchemaItem(
                "ST-08",
                "Hospital / Medical Center",
                "Multi-department facility with operating rooms, ICU, labs and clinical suites",
                SchemaCategory.SPACE_TYPE,
                "LocalHospital"
            ),
            SchemaItem(
                "ST-09",
                "School / Academy",
                "Educational facility with classrooms, labs, lecture halls and training rooms",
                SchemaCategory.SPACE_TYPE,
                "School"
            ),
            SchemaItem(
                "ST-10",
                "Corporate Campus / Institution",
                "Multi-building compound — universities, NGOs, government or corporate campuses",
                SchemaCategory.SPACE_TYPE,
                "AccountBalance"
            ),
            SchemaItem(
                "ST-11",
                "Event Venue / Banquet Hall",
                "Dedicated events property — weddings, corporate galas, product launches",
                SchemaCategory.SPACE_TYPE,
                "Celebration"
            ),
            SchemaItem(
                "ST-12",
                "Sports Complex",
                "Multi-purpose sports facility with courts, pools, tracks and fitness halls",
                SchemaCategory.SPACE_TYPE,
                "SportsSoccer"
            ),
            SchemaItem(
                "ST-13",
                "Beauty / Wellness Center",
                "Salon stations, spa treatment rooms, therapy suites and beauty studios",
                SchemaCategory.SPACE_TYPE,
                "Spa"
            ),
            SchemaItem(
                "ST-14",
                "Pharmacy / Diagnostic Lab",
                "Dispensaries, clinical testing labs and medical imaging centers",
                SchemaCategory.SPACE_TYPE,
                "Biotech"
            ),
            SchemaItem(
                "ST-15",
                "Legal & Professional Office",
                "Law firms, accounting offices and notary consultation suites",
                SchemaCategory.SPACE_TYPE,
                "Gavel"
            ),
            SchemaItem("ST-16", "Hybrid Work Hub", "Combined coworking + private offices + meeting rooms in one compound", SchemaCategory.SPACE_TYPE, "Hub")
        ),
        divisionTypes = listOf(
            SchemaItem("DT-01", "Room / Dedicated Suite", "Independent private room within premises", SchemaCategory.DIVISION_TYPE, "MeetingRoom"),
            SchemaItem("DT-02", "Office", "Self-contained private office unit", SchemaCategory.DIVISION_TYPE, "Business"),
            SchemaItem(
                "DT-03",
                "Conference Room",
                "Equipped boardroom with A/V presentation hardware",
                SchemaCategory.DIVISION_TYPE,
                "CoPresent",
                supportsAttendeeMode = true
            ),
            SchemaItem(
                "DT-04",
                "Theater / Training Room",
                "High-capacity seminar and workshop hall with stage",
                SchemaCategory.DIVISION_TYPE,
                "School",
                supportsAttendeeMode = true
            ),
            SchemaItem("DT-05", "Desk in Shared Area", "Dedicated hot desk with ergonomic seating", SchemaCategory.DIVISION_TYPE, "Desk"),
            SchemaItem("DT-06", "Gym", "Exercise and fitness facility", SchemaCategory.DIVISION_TYPE, "FitnessCenter"),
            SchemaItem("DT-07", "Studio", "Creative or media production studio", SchemaCategory.DIVISION_TYPE, "Videocam"),
            SchemaItem("DT-08", "Storage", "Secure storage or archive space", SchemaCategory.DIVISION_TYPE, "Inventory2"),
            SchemaItem("DT-09", "Clinical Booth", "Sanitized treatment station with examination bed", SchemaCategory.DIVISION_TYPE, "MedicalServices"),
            SchemaItem("DT-10", "Sports Area", "Multi-purpose sports or rehabilitation zone", SchemaCategory.DIVISION_TYPE, "SportsSoccer"),
            SchemaItem(
                "DT-11",
                "Boardroom",
                "Executive-level meeting suite (4–12 seats) with premium A/V",
                SchemaCategory.DIVISION_TYPE,
                "TableRestaurant",
                supportsAttendeeMode = true
            ),
            SchemaItem(
                "DT-12",
                "Auditorium / Theater Hall",
                "Large-capacity hall (100+ seats) with fixed seating and stage",
                SchemaCategory.DIVISION_TYPE,
                "TheaterComedy",
                supportsAttendeeMode = true
            ),
            SchemaItem(
                "DT-13",
                "Seminar Room",
                "Classroom-style layout (20–50 seats) with lectern and display",
                SchemaCategory.DIVISION_TYPE,
                "Groups",
                supportsAttendeeMode = true
            ),
            SchemaItem(
                "DT-14",
                "Conference Hall",
                "Banquet or event hall for 50–500 attendees with catering space",
                SchemaCategory.DIVISION_TYPE,
                "Celebration",
                supportsAttendeeMode = true
            ),
            SchemaItem("DT-15", "Whole Floor / Wing", "Lease of an entire building floor, wing or section", SchemaCategory.DIVISION_TYPE, "Layers"),
            SchemaItem("DT-16", "VIP Lounge", "Premium networking and reception space with lounge furniture", SchemaCategory.DIVISION_TYPE, "Star"),
            SchemaItem(
                "DT-17",
                "Podcast / Recording Studio",
                "Sound-treated audio production suite with acoustic treatment",
                SchemaCategory.DIVISION_TYPE,
                "Mic"
            ),
            SchemaItem(
                "DT-18",
                "Training Lab",
                "Computer lab with individual workstations for hands-on training",
                SchemaCategory.DIVISION_TYPE,
                "Computer"
            ),
            SchemaItem("DT-19", "Operating Theater", "Sterile surgical suite for specialized medical procedures", SchemaCategory.DIVISION_TYPE, "Vaccines"),
            SchemaItem("DT-20", "Outdoor Area / Terrace", "Rooftop, garden or covered outdoor event space", SchemaCategory.DIVISION_TYPE, "Park")
        ),
        facilities = listOf(
            SchemaItem("FAC-01", "24/7 Solar & Generator Backup", "Continuous uninterrupted power supply across Lebanon", SchemaCategory.FACILITY, "Bolt"),
            SchemaItem(
                "FAC-02",
                "High-Speed Fiber Wi-Fi (100+ Mbps)",
                "Redundant ultra-fast Internet with backup 4G router",
                SchemaCategory.FACILITY,
                "Wifi"
            ),
            SchemaItem(
                "FAC-03",
                "Receptionist & Front Desk Support",
                "Professional greeting for visiting clients and patients",
                SchemaCategory.FACILITY,
                "SupportAgent"
            ),
            SchemaItem("FAC-04", "Client Waiting Lounge", "Spacious waiting area with comfortable seating", SchemaCategory.FACILITY, "Weekend"),
            SchemaItem("FAC-05", "Kitchenette & Espresso Bar", "Complimentary Lebanese coffee, espresso, and tea", SchemaCategory.FACILITY, "Coffee"),
            SchemaItem(
                "FAC-06",
                "Elevator & Wheelchair Access",
                "Accessible entrance complying with Lebanese building codes",
                SchemaCategory.FACILITY,
                "Elevator"
            ),
            SchemaItem(
                "FAC-07",
                "Dedicated Underground Parking",
                "Secured reserved parking bays for practitioners",
                SchemaCategory.FACILITY,
                "LocalParking"
            ),
            SchemaItem("FAC-08", "HVAC Climate Control", "Central air-conditioning and heating system", SchemaCategory.FACILITY, "AcUnit")
        ),
        amenities = listOf(
            // Comfort
            SchemaItem(
                "AM-01",
                "A/C Climate Control",
                "Individual room temperature management",
                SchemaCategory.AMENITY,
                "AcUnit",
                amenityGroup = "Comfort"
            ),
            SchemaItem("AM-02", "Natural Lighting", "Large windows with natural daylight", SchemaCategory.AMENITY, "WbSunny", amenityGroup = "Comfort"),
            SchemaItem("AM-03", "Ergonomic Seating", "Adjustable lumbar-support chairs", SchemaCategory.AMENITY, "Chair", amenityGroup = "Comfort"),
            SchemaItem(
                "AM-04",
                "Soundproofing",
                "Acoustic wall panels for private sessions",
                SchemaCategory.AMENITY,
                "VolumeOff",
                amenityGroup = "Comfort"
            ),
            SchemaItem(
                "AM-05",
                "Standing Desk",
                "Height-adjustable motorized standing desk",
                SchemaCategory.AMENITY,
                "DesktopMac",
                amenityGroup = "Comfort"
            ),
            // Access
            SchemaItem(
                "AM-06",
                "Keyless Access Control",
                "Smart digital lock with PIN or card entry",
                SchemaCategory.AMENITY,
                "VpnKey",
                amenityGroup = "Access"
            ),
            SchemaItem(
                "AM-07",
                "Privacy Partition",
                "Floor-to-ceiling sliding partition screen",
                SchemaCategory.AMENITY,
                "TableRows",
                amenityGroup = "Access"
            ),
            SchemaItem("AM-08", "Storage Locker", "Personal lockable storage compartment", SchemaCategory.AMENITY, "Lock", amenityGroup = "Access"),
            // Tech
            SchemaItem("AM-09", "High-Speed Wi-Fi", "Dedicated fiber broadband access point", SchemaCategory.AMENITY, "Wifi", amenityGroup = "Tech"),
            SchemaItem("AM-10", "Dual-Monitor Setup", "Two full-HD monitors with HDMI dock", SchemaCategory.AMENITY, "Monitor", amenityGroup = "Tech"),
            SchemaItem(
                "AM-11",
                "Whiteboard / Presentation Kit",
                "Magnetic glass board with HDMI screen",
                SchemaCategory.AMENITY,
                "PresentToAll",
                amenityGroup = "Tech"
            ),
            // Equipment
            SchemaItem(
                "AM-12",
                "Motorized Standing Desk & Ergonomic Chair",
                "Programmable height desk with lumbar chair",
                SchemaCategory.AMENITY,
                "DesktopMac",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-13",
                "Executive Conference Table (Seats 8)",
                "Oval boardroom table for 8 with cable channels",
                SchemaCategory.AMENITY,
                "TableBar",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-14",
                "4K Ultra-HD Presentation Screen",
                "86\" 4K commercial display with Apple TV",
                SchemaCategory.AMENITY,
                "Tv",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-15",
                "High-Speed Laser Printer / Scanner",
                "A3/A4 mono laser MFP, 45 ppm",
                SchemaCategory.AMENITY,
                "Print",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-16",
                "Video Conferencing Camera & Mic Pod",
                "360° auto-tracking camera with full-duplex pod",
                SchemaCategory.AMENITY,
                "Videocam",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-17",
                "Studio Softbox Lighting Kit",
                "2× 150W softbox stands with diffusers",
                SchemaCategory.AMENITY,
                "LightMode",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-18",
                "Soundproof Acoustic Booth",
                "Freestanding vocal isolation booth",
                SchemaCategory.AMENITY,
                "VolumeOff",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-19",
                "Workstation PC Dual-Monitor Setup",
                "Intel i9 workstation, 32 GB RAM, dual 27\" 4K",
                SchemaCategory.AMENITY,
                "Computer",
                amenityGroup = "Equipment"
            ),
            SchemaItem(
                "AM-20",
                "Magnetic Glass Presentation Whiteboard",
                "Floor-mounted magnetic glass writing surface",
                SchemaCategory.AMENITY,
                "PresentToAll",
                amenityGroup = "Equipment"
            ),
            // Clinical
            SchemaItem(
                "AM-21",
                "Examination Bed & Lighting",
                "Height-adjustable clinical bed with overhead light",
                SchemaCategory.AMENITY,
                "MedicalInformation",
                amenityGroup = "Clinical",
                scopedToIds = listOf("DT-09")
            ),
            SchemaItem(
                "AM-22",
                "Diagnostic Equipment Station",
                "Sphygmomanometer, oximeter, ECG station",
                SchemaCategory.AMENITY,
                "Biotech",
                amenityGroup = "Clinical",
                scopedToIds = listOf("DT-09")
            ),
            SchemaItem(
                "AM-23",
                "Sterilization & Biohazard Unit",
                "Medical-grade autoclave and sharps disposal",
                SchemaCategory.AMENITY,
                "Sanitizer",
                amenityGroup = "Clinical",
                scopedToIds = listOf("DT-09")
            )
        ),
        rentalStrategies = listOf(
            SchemaItem(
                "RS-01",
                "Full Month (Exclusive)",
                "Continuous 30-day dedicated exclusive workspace lease",
                SchemaCategory.RENTAL_STRATEGY,
                "CalendarMonth"
            ),
            SchemaItem(
                "RS-02",
                "Shift-Based (Morning / Afternoon)",
                "Scheduled time blocks (e.g. 08:00–13:00 or 14:00–19:00)",
                SchemaCategory.RENTAL_STRATEGY,
                "Schedule"
            ),
            SchemaItem(
                "RS-03",
                "Day-per-Week Basis",
                "Recurring weekly dedicated days (e.g. Every Tue & Thu)",
                SchemaCategory.RENTAL_STRATEGY,
                "DateRange"
            ),
            SchemaItem("RS-04", "Hourly / On-Demand Slot", "Flexible hourly pass with 2-hour minimum booking", SchemaCategory.RENTAL_STRATEGY, "Timelapse")
        ),
        attendeePackages = listOf(
            AttendeePackage(
                id = "APK-01",
                name = "Venue Only",
                description = "Space and seating only — no catering or additional services included",
                pricePerAttendeeUsd = 5.0,
                inclusions = listOf("Venue access", "Basic seating setup"),
                minAttendees = 1
            ),
            AttendeePackage(
                id = "APK-02",
                name = "Standard Package",
                description = "Venue with refreshments and standard A/V setup",
                pricePerAttendeeUsd = 10.0,
                inclusions = listOf("Venue access", "Coffee & water", "Juices", "Projector & screen", "Wi-Fi"),
                minAttendees = 5
            ),
            AttendeePackage(
                id = "APK-03",
                name = "Full Day Package",
                description = "Complete all-day conference package with catering and full A/V",
                pricePerAttendeeUsd = 18.0,
                inclusions = listOf("Venue access", "Breakfast", "Lunch", "Coffee breaks", "Full A/V equipment", "Wi-Fi", "Stationery kit"),
                minAttendees = 10
            ),
            AttendeePackage(
                id = "APK-04",
                name = "VIP Package",
                description = "Premium event experience with full catering and on-site A/V technician",
                pricePerAttendeeUsd = 35.0,
                inclusions = listOf(
                    "Premium venue setup",
                    "Full catering (3 meals)",
                    "Welcome reception",
                    "Premium A/V + technician",
                    "Branded signage",
                    "Wi-Fi",
                    "Gift bags"
                ),
                minAttendees = 20
            )
        )
    )
}
