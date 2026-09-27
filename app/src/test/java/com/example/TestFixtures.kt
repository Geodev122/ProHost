package com.example

import com.example.data.model.*

/** Demo listing used by repository tests (production no longer seeds any). */
fun demoSpaces(): List<SpaceListing> = listOf(
            SpaceListing(
                id = "SP-001",
                title = "Achrafieh Executive Medical Suite",
                spaceType = SpaceType.POLYCLINIC,
                governorate = Governorate.BEIRUT,
                district = "Achrafieh",
                streetAddress = "Sursock Street, Beirut",
                floorInfo = "2nd Floor, Suite 204",
                lat = 33.8886,
                lng = 35.5142,
                isShared = true,
                complementarySpecialties = listOf("Cardiology", "Dermatology", "Pediatrics"),
                residentPractitioners = listOf("Dr. Sami Haddad"),
                essentialFacilities = listOf("High-Speed Wi-Fi", "Receptionist", "Sterilization Suite"),
                equipment = listOf(EquipmentItem("EQ-1", "Exam Table", EquipmentCategory.WORKSPACES, 1, "Hydraulic exam table")),
                rentalFormulas = listOf(
                    RentalFormula(
                        id = "F1",
                        type = RentalFormulaType.SHIFT,
                        rateUsd = 350.0,
                        scheduleDescription = "Morning Shift (08:00 - 14:00)",
                        daysOfWeek = listOf("Mon", "Wed", "Fri"),
                        startHour = "08:00",
                        endHour = "14:00",
                        totalWeeklyHours = 18,
                        shiftName = "Morning Shift"
                    )
                ),
                rules = PremisesRules(),
                ownerId = "USR-OWNER-01",
                ownerName = "Achrafieh Commercial Properties",
                ownerPhone = "+961 3 123456",
                ownerEmail = "host.achrafieh@prohost.lb",
                baseMonthlyRateUsd = 450.0
            )
        )
