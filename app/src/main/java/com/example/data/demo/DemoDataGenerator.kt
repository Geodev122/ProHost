package com.example.data.demo

import com.example.data.model.*

/**
 * Generator for genuine-looking demo content (users, workspace listings, and fake booking requests)
 * designed for client demonstrations, testing, and UI validation before production deployment.
 * All items generated here carry `isDemo = true` and can be purged at any time from the Admin Console.
 */
object DemoDataGenerator {

    fun generateDemoUsers(): List<AppUser> {
        val now = System.currentTimeMillis()
        return listOf(
            AppUser(
                id = "demo-host-01",
                email = "demo.host.achrafieh@prohost.lb",
                fullName = "Dr. Elie Haddad",
                role = UserRole.PRO_HOST,
                specialty = "Orthopedic & Surgical Specialist",
                phone = "+9613123456",
                country = "Lebanon",
                governorate = Governorate.BEIRUT.name,
                city = "Achrafieh",
                isVerified = true,
                ownerPackageId = "pkg_priority",
                ownerPackageExpiryMillis = now + 30 * 86400000L,
                emailVerified = true,
                isDemo = true
            ),
            AppUser(
                id = "demo-host-02",
                email = "demo.host.verdun@prohost.lb",
                fullName = "Nour El-Khoury",
                role = UserRole.PRO_HOST,
                specialty = "Medical Facility Management",
                phone = "+9611223344",
                country = "Lebanon",
                governorate = Governorate.BEIRUT.name,
                city = "Verdun",
                isVerified = true,
                ownerPackageId = "pkg_priority",
                ownerPackageExpiryMillis = now + 30 * 86400000L,
                emailVerified = true,
                isDemo = true
            ),
            AppUser(
                id = "demo-spec-01",
                email = "demo.specialist.layla@medical.lb",
                fullName = "Dr. Layla Karam",
                role = UserRole.SPECIALIST,
                specialty = "Dermatology & Aesthetic Medicine",
                phone = "+96170112233",
                country = "Lebanon",
                governorate = Governorate.BEIRUT.name,
                city = "Achrafieh",
                isVerified = true,
                emailVerified = true,
                isDemo = true
            ),
            AppUser(
                id = "demo-spec-02",
                email = "demo.specialist.tariq@dental.lb",
                fullName = "Dr. Tariq Mansour",
                role = UserRole.SPECIALIST,
                specialty = "Orthodental Surgery",
                phone = "+9613998877",
                country = "Lebanon",
                governorate = Governorate.BEIRUT.name,
                city = "Badaro",
                isVerified = true,
                emailVerified = true,
                isDemo = true
            ),
            AppUser(
                id = "demo-spec-03",
                email = "demo.specialist.maya@pediatrics.lb",
                fullName = "Dr. Maya Ziad",
                role = UserRole.SPECIALIST,
                specialty = "Pediatrics & Child Health",
                phone = "+96171554433",
                country = "Lebanon",
                governorate = Governorate.MOUNT_LEBANON.name,
                city = "Hazmieh",
                isVerified = true,
                emailVerified = true,
                isDemo = true
            )
        )
    }

    fun generateDemoListings(): List<SpaceListing> {
        val f1 = RentalFormula(
            id = "demo-frm-01",
            type = RentalFormulaType.SHIFT,
            rateUsd = 350.0,
            scheduleDescription = "Morning Shift (08:00 - 14:00)",
            daysOfWeek = listOf("Mon", "Wed", "Fri"),
            startHour = "08:00",
            endHour = "14:00",
            totalWeeklyHours = 18,
            shiftName = "Morning Shift"
        )

        val f2 = RentalFormula(
            id = "demo-frm-02",
            type = RentalFormulaType.DAY_PER_WEEK,
            rateUsd = 600.0,
            scheduleDescription = "Full Day Access (2 Days/Wk)",
            daysOfWeek = listOf("Tue", "Thu"),
            startHour = "08:00",
            endHour = "18:00",
            totalWeeklyHours = 20
        )

        val f3 = RentalFormula(
            id = "demo-frm-03",
            type = RentalFormulaType.HOURLY,
            rateUsd = 40.0,
            scheduleDescription = "Hourly Slot Access",
            daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
            startHour = "08:00",
            endHour = "20:00",
            minHours = 2
        )

        return listOf(
            SpaceListing(
                id = "demo-space-001",
                title = "Achrafieh Executive Medical Suite",
                description = "Fully furnished medical polyclinic with state-of-the-art diagnostic equipment & reception lounge.",
                spaceType = SpaceType.POLYCLINIC,
                spaceCategoryId = "POLYCLINIC",
                spaceCategoryName = "Polyclinic",
                governorate = Governorate.BEIRUT,
                district = "Achrafieh",
                streetAddress = "Sursock Street, Suite 204",
                floorInfo = "2nd Floor",
                lat = 33.8886,
                lng = 35.5142,
                isShared = true,
                complementarySpecialties = listOf("Cardiology", "Dermatology", "Pediatrics"),
                residentPractitioners = listOf("Dr. Elie Haddad", "Dr. Youssef Khoury"),
                essentialFacilities = listOf("High-Speed Wi-Fi", "Receptionist Lounge", "Sterilization Suite", "24/7 Generator"),
                equipment = listOf(
                    EquipmentItem("EQ-D1", "Hydraulic Exam Table", EquipmentCategory.WORKSPACES, 1, "Electric position control"),
                    EquipmentItem("EQ-D2", "Echocardiogram 4K Doppler", EquipmentCategory.WORKSPACES, 1, "4K Color Doppler")
                ),
                pricing = RentalPricingConfig.fromLegacyFormula(f1),
                rentalFormulas = listOf(f1, f2, f3),
                rules = PremisesRules(
                    smokingAllowed = false,
                    foodAllowed = true,
                    visitorPolicy = "Clients & visitors welcomed in reception lounge"
                ),
                ownerId = "demo-host-01",
                ownerName = "Dr. Elie Haddad",
                ownerPhone = "+9613123456",
                ownerEmail = "demo.host.achrafieh@prohost.lb",
                baseMonthlyRateUsd = 850.0,
                status = ListingStatus.ACTIVE,
                isVerified = true,
                ownershipProofUrl = "https://storage.example.com/deed_demo_001.pdf",
                imageUrls = listOf("https://images.unsplash.com/photo-1629909613654-28e377c37b09"),
                subdivisions = listOf(
                    Subdivision(
                        id = "demo-sub-001",
                        name = "Cardiology Bay A",
                        type = Level2Type.ROOMS,
                        amenities = listOf("ECG Machine", "Patient Exam Bed", "Doctor Desk"),
                        pricing = RentalPricingConfig.fromLegacyFormula(f1)
                    ),
                    Subdivision(
                        id = "demo-sub-002",
                        name = "Dermatology Clinic B",
                        type = Level2Type.ROOMS,
                        amenities = listOf("Surgical Light", "Sterilization Tray"),
                        pricing = RentalPricingConfig.fromLegacyFormula(f2)
                    )
                ),
                isDemo = true
            ),
            SpaceListing(
                id = "demo-space-002",
                title = "Badaro Dental & Surgical Hub",
                description = "Modern dental clinic with autonomous sterilization, digital X-ray, and hydraulic chair.",
                spaceType = SpaceType.CENTER,
                spaceCategoryId = "CENTER",
                spaceCategoryName = "Medical Center",
                governorate = Governorate.BEIRUT,
                district = "Badaro",
                streetAddress = "Badaro Main Street, Center 12",
                floorInfo = "1st Floor",
                lat = 33.8750,
                lng = 35.5200,
                isShared = true,
                complementarySpecialties = listOf("Dentistry", "Orthodontics"),
                residentPractitioners = listOf("Dr. Tariq Mansour"),
                essentialFacilities = listOf("Dental Chair", "X-Ray Autoclave", "Waiting Lounge"),
                equipment = listOf(EquipmentItem("EQ-D3", "Full Dental Surgery Chair", EquipmentCategory.WORKSPACES, 1, "Hydraulic control")),
                pricing = RentalPricingConfig.fromLegacyFormula(f2),
                rentalFormulas = listOf(f2),
                rules = PremisesRules(),
                ownerId = "demo-host-01",
                ownerName = "Dr. Elie Haddad",
                ownerPhone = "+9613123456",
                ownerEmail = "demo.host.achrafieh@prohost.lb",
                baseMonthlyRateUsd = 950.0,
                status = ListingStatus.ACTIVE,
                isVerified = true,
                imageUrls = listOf("https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d"),
                isDemo = true
            ),
            SpaceListing(
                id = "demo-space-003",
                title = "Verdun Wellness & Consultation Suites",
                description = "Quiet private consultation office ideal for psychological, nutrition, and holistic wellness practice.",
                spaceType = SpaceType.PRIVATE_OFFICE,
                spaceCategoryId = "PRIVATE_OFFICE",
                spaceCategoryName = "Private Office",
                governorate = Governorate.BEIRUT,
                district = "Verdun",
                streetAddress = "Verdun Street, Suite 501",
                floorInfo = "5th Floor",
                lat = 33.8850,
                lng = 35.4850,
                isShared = false,
                complementarySpecialties = listOf("Psychology", "Nutrition"),
                residentPractitioners = listOf("Nour El-Khoury"),
                essentialFacilities = listOf("Private Restroom", "High-Speed Internet", "Janitorial Service"),
                equipment = emptyList(),
                pricing = RentalPricingConfig.fromLegacyFormula(f1),
                rentalFormulas = listOf(f1),
                rules = PremisesRules(),
                ownerId = "demo-host-02",
                ownerName = "Nour El-Khoury",
                ownerPhone = "+9611223344",
                ownerEmail = "demo.host.verdun@prohost.lb",
                baseMonthlyRateUsd = 700.0,
                status = ListingStatus.ACTIVE,
                isVerified = true,
                imageUrls = listOf("https://images.unsplash.com/photo-1586773860418-d37222d8fce3"),
                isDemo = true
            ),
            SpaceListing(
                id = "demo-space-004",
                title = "Hazmieh Modern Orthopedic Center",
                description = "Draft workspace suite pending final equipment list setup and publishing authorization.",
                spaceType = SpaceType.POLYCLINIC,
                spaceCategoryId = "POLYCLINIC",
                spaceCategoryName = "Polyclinic",
                governorate = Governorate.MOUNT_LEBANON,
                district = "Hazmieh",
                streetAddress = "Damascus Highway",
                floorInfo = "Ground Floor",
                lat = 33.8500,
                lng = 35.5400,
                isShared = true,
                complementarySpecialties = listOf("Orthopedics"),
                residentPractitioners = emptyList(),
                essentialFacilities = listOf("24/7 Generator", "Elevator"),
                equipment = emptyList(),
                rentalFormulas = listOf(f1),
                rules = PremisesRules(),
                ownerId = "demo-host-02",
                ownerName = "Nour El-Khoury",
                ownerPhone = "+9611223344",
                ownerEmail = "demo.host.verdun@prohost.lb",
                baseMonthlyRateUsd = 600.0,
                status = ListingStatus.DRAFT,
                isVerified = false,
                isDemo = true
            )
        )
    }

    fun generateDemoBookings(): List<BookingRequest> {
        val listings = generateDemoListings()
        val space1 = listings[0]
        val space2 = listings[1]
        val space3 = listings[2]

        return listOf(
            BookingRequest(
                id = "demo-booking-001",
                spaceId = space1.id,
                spaceTitle = space1.title,
                spaceDistrict = space1.district,
                governorate = space1.governorate,
                ownerId = space1.ownerId,
                ownerName = space1.ownerName,
                ownerPhone = space1.ownerPhone,
                practitionerId = "demo-spec-01",
                practitionerName = "Dr. Layla Karam",
                practitionerEmail = "demo.specialist.layla@medical.lb",
                practitionerPhone = "+96170112233",
                practitionerSpecialty = "Dermatologist",
                formula = space1.rentalFormulas.first(),
                startDate = "2026-10-01",
                endDate = "2026-12-31",
                selectedDays = listOf("Mon", "Wed", "Fri"),
                selectedStartHour = "08:00",
                selectedEndHour = "14:00",
                selectedShift = "Morning Shift",
                selectedDateTimeRange = "Mon, Wed, Fri (08:00 - 14:00)",
                durationMonths = 3,
                totalAmountUsd = 1050.0,
                clinicalNotes = "Dermatology & minor outpatient procedures clinic",
                status = BookingRequestStatus.ACCEPTED,
                agreementUrl = "https://storage.example.com/agreements/demo_agreement_001.pdf",
                paymentAcknowledgedByHost = false,
                paymentAcknowledgedBySpecialist = true,
                isDemo = true
            ),
            BookingRequest(
                id = "demo-booking-002",
                spaceId = space2.id,
                spaceTitle = space2.title,
                spaceDistrict = space2.district,
                governorate = space2.governorate,
                ownerId = space2.ownerId,
                ownerName = space2.ownerName,
                ownerPhone = space2.ownerPhone,
                practitionerId = "demo-spec-02",
                practitionerName = "Dr. Tariq Mansour",
                practitionerEmail = "demo.specialist.tariq@dental.lb",
                practitionerPhone = "+9613998877",
                practitionerSpecialty = "Orthodental Surgeon",
                formula = space2.rentalFormulas.first(),
                startDate = "2026-10-15",
                endDate = "2026-11-15",
                selectedDays = listOf("Tue", "Thu"),
                selectedStartHour = "08:00",
                selectedEndHour = "18:00",
                selectedShift = "Full Day",
                selectedDateTimeRange = "Tue, Thu (08:00 - 18:00)",
                durationMonths = 1,
                totalAmountUsd = 600.0,
                clinicalNotes = "Dental surgery and aligner consultations",
                status = BookingRequestStatus.PENDING,
                isDemo = true
            ),
            BookingRequest(
                id = "demo-booking-003",
                spaceId = space3.id,
                spaceTitle = space3.title,
                spaceDistrict = space3.district,
                governorate = space3.governorate,
                ownerId = space3.ownerId,
                ownerName = space3.ownerName,
                ownerPhone = space3.ownerPhone,
                practitionerId = "demo-spec-03",
                practitionerName = "Dr. Maya Ziad",
                practitionerEmail = "demo.specialist.maya@pediatrics.lb",
                practitionerPhone = "+96171554433",
                practitionerSpecialty = "Pediatrician",
                formula = space3.rentalFormulas.first(),
                startDate = "2026-11-01",
                endDate = "2026-12-01",
                selectedDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                selectedStartHour = "08:00",
                selectedEndHour = "18:00",
                selectedShift = "Exclusive Monthly",
                selectedDateTimeRange = "Mon-Fri Exclusive Monthly",
                durationMonths = 1,
                totalAmountUsd = 700.0,
                clinicalNotes = "Pediatric health consultations & growth tracking",
                status = BookingRequestStatus.PENDING,
                isDemo = true
            )
        )
    }
}
