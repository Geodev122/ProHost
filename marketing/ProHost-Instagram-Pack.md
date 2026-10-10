# ProHost — Instagram pack

## ★ Feed posts with music (`posts/`): use these
Ten animated **feed posts at 4:5 (1080×1350)**, the full-size feed format, 16 s each. They tell the same second-person story as
the Reels below, with the same tap-through app interactions and photo backgrounds, but **no voiceover**. Each one has its own
background track, made with Vertex AI **Lyria** (instrumental, royalty-free to use), plus light sound effects on the animation:
- **Music shifted to the animation:** the mixer (`src/mix.py`) slides each 33 s Lyria track until its strongest beats land on
  the post's scene changes, the incoming request, the Accept/Send tap and the 50% badge.
- **Sound effects:** a whoosh on each scene change, a pop on cards, a soft click on taps, a chime for a new request, a sparkle
  when a request is sent or accepted, and an impact on the 50% badge. The music dips for a breath just before the offer.
- **Loudness:** -14 LUFS, Instagram's level. Video H.264 + AAC 192 kb/s.
- **Files:** `posts/<post>.mp4`, `posts/<post>_cover.png` (cover) and `posts/<post>_grid.png` (the 3:4 crop the profile grid shows).
  All text stays at least 60 px from the edges, so the grid crop never cuts anything.

| Post | Music (Lyria prompt) |
|---|---|
| post1_hosts | upbeat corporate house |
| post2_app | bright pop electronic |
| post3_psychologist | calm lo-fi, Rhodes piano |
| post4_trainer | energetic motivational electronic |
| post5_lifecoach | feel-good acoustic pop, ukulele |
| post6_dietitian | light tropical house, marimba |
| post7_hotel | smooth deep house lounge |
| post8_medical | clean minimal corporate pop |
| post9_institute | inspiring indie electronic |
| post10_studio | relaxed organic downtempo |

Regenerate the music: Actions › "Marketing music (manual)" (`scripts/marketing/music.json`). Re-render and re-score:
`src/README.md`. Captions and hashtags: the same as each Reel below.

---

## Reels with Lebanese voiceover (9:16, `final/`)

**Format (Instagram Reels, 2026):** 1080×1920 (9:16), 16 s, 30 fps, H.264 + AAC. Every word and card sits inside Instagram's
safe zone: clear of the top bar (top 250 px), the caption and buttons (bottom ~420 px) and the right-hand action rail. The
profile grid shows Reels as a **3:4** crop of the cover, so each post also has a `_grid.png` (1080×1440) preview, and the
key content stays inside that crop.

**Story:** every Reel talks to the viewer: *You're a… / You run a…* → the problem → *With ProHost / Now you can / Finally…*
→ the solution in the app → **Download ProHost** + **50% off your first ProHost Premium subscription, monthly or yearly**.
Renter Reels add "Booking is free · Premium is for listing your space", so the offer stays accurate for them.

**Files**
- `final/<post>_final.mp4`: the finished Reel with its voiceover. Post this one.
- `<post>.mp4`: the silent video. `voice/<post>_voice.mp3`/`.wav`: the voice track on its own (16.0 s, levelled to -16 LUFS).
- `<post>_cover.png` (Reel cover) and `<post>_grid.png` (3:4 grid preview).
- `bg/*.jpg`: the photo backgrounds, photorealistic interiors generated with Vertex AI (Gemini image). There are no real people,
  places or brands in them. Regenerate: Actions › "Marketing backgrounds (manual)" (`scripts/marketing/backgrounds.json`).

**Voices:** Vertex AI's newest Gemini TTS (`gemini-3.1-flash-tts-preview`), told to sound like a relaxed Lebanese voice note,
with technical words in English. Male and female voices alternate across the 10 Reels. Edit the lines in
`scripts/marketing/voiceover.json`, then run Actions › "Marketing voiceovers (manual)".

**Before posting:** the yearly 50% offer must be live in Play Console (today only the monthly one is). Add music from
Instagram's library at 15–20% under the voice.

---

## Space owners (general) (`final/post1_hosts_final.mp4`)
**Background:** office, clinic, studio, meeting room (they change with the words). **On screen:** "You have an office. A clinic. A studio. A meeting room. Sitting empty half the week?" → "With ProHost, rent it on your terms" (rental types + week calendar) → "Run it all from one app" → Download ProHost + 50% off.

**Voiceover (Lebanese Arabic):**
**Voice:** `Achird` (male) · file `voice/post1_hosts_voice.mp3`

> **0.25–3.55 s** · عِنْدَك office، clinic أو studio… وقاعِد فاضي نُصّ الجِمْعَة؟
>
> **3.80–8.05 s** · مع ProHost، أَجّْرو بِالساعَة، بِالـ shift، بِاليوم أو بِالشَّهِر.
>
> **8.40–11.40 s** · الـ requests، الـ bookings والأَسْعار… بِـ app واحِد.
>
> **11.65–15.90 s** · نَزِّل ProHost، وأَوَّل اشْتِراك Premium بْنُصّ السِّعِر، monthly أو yearly.

**Caption:**
Your office, clinic or meeting room doesn't have to sit empty. 🗝️
With ProHost you rent it your way: by the hour, the shift, the day, the month, or per attendee. Requests, availability and prices live in one app.
🎉 Launch offer: 50% off your first ProHost Premium subscription, monthly or yearly.
📲 Download ProHost on Google Play, link in bio.
**Hashtags:** #ProHost #Lebanon #Beirut #OfficeSpace #ClinicForRent #MeetingRoom #PropertyOwners #FlexibleWorkspace #PassiveIncome

---

## The app (general) (`final/post2_app_final.mp4`)
**Background:** coworking space. **On screen:** "You need a space, not a lease." → the app: sign-in, Explore map, real-time availability, WhatsApp, host financials → Download ProHost + 50% off.

**Voiceover (Lebanese Arabic):**
**Voice:** `Sulafat` (female) · file `voice/post2_app_voice.mp3`

> **0.30–2.90 s** · بَدَّك مَحَل تِشْتِغِل فيه… مِش عَقْد؟
>
> **3.10–5.90 s** · مع ProHost، الـ clinics والـ offices كِلُّن عَ الـ map.
>
> **6.10–8.70 s** · احْجُز بِالساعَة، وِحْكي الـ host عَ WhatsApp.
>
> **8.90–11.40 s** · وإذا عِنْدَك space، أَجّْرا مِن نَفْس الـ app.
>
> **11.70–15.90 s** · نَزِّل ProHost، وأَوَّل اشْتِراك Premium بْنُصّ السِّعِر، monthly أو yearly.

**Caption:**
You need a space, not a lease. 🇱🇧
🗺️ Clinics, offices, studios and halls on one live map
⏱️ Book by the hour, shift, day or month, or per attendee
💬 Talk to the host on WhatsApp
📲 Download ProHost on Google Play. Booking is free.
🎉 Own a space? 50% off your first ProHost Premium subscription, monthly or yearly.
**Hashtags:** #ProHost #Lebanon #Beirut #Workspace #RentAnOffice #ClinicSpace #Freelancers #Therapists #LebanonBusiness

---

## You're a psychologist (by the shift) (`final/post3_psychologist_final.mp4`)
**Background:** calm therapy room. **On screen:** "You're a psychologist. You don't need a clinic all week. Just two mornings." → "Now you can book by the shift" → Download ProHost + offer.

**Voiceover (Lebanese Arabic):**
**Voice:** `Algieba` (male) · file `voice/post3_psychologist_voice.mp3`

> **0.30–4.55 s** · إنتَ psychologist، وما بْتِحْتاج clinic كِل الجِمْعَة… بَس يومين؟
>
> **4.80–10.90 s** · هَلَّأ فيك، مع ProHost، تِحْجُز الـ morning shift التَّلاتا والخَميس، وتِدْفَع بَس عَ وَئْتَك.
>
> **11.15–15.90 s** · نَزِّل ProHost، الـ booking بِبَلاش… وإذا عِنْدَك space، أَوَّل Premium بْنُصّ السِّعِر.

**Caption:**
You're a psychologist. You don't need a clinic all week, just two mornings. 🧠
Now you can book a calm therapy room by the shift and pay only for the hours you see clients.
📲 Download ProHost on Google Play. Booking is free.
🎉 Own a space? 50% off your first ProHost Premium subscription, monthly or yearly.
**Hashtags:** #Psychologist #TherapistLebanon #PrivatePractice #ClinicRental #Beirut #ProHost #MentalHealthLebanon

---

## You're a corporate trainer (per attendee) (`final/post4_trainer_final.mp4`)
**Background:** training room. **On screen:** "You're a corporate trainer. 18 trainees on Saturday. And no proper room." → "With ProHost, pay per seat" → Download ProHost + offer.

**Voiceover (Lebanese Arabic):**
**Voice:** `Despina` (female) · file `voice/post4_trainer_voice.mp3`

> **0.30–4.55 s** · إنتَ trainer، وعِنْدَك workshop لَتْمَنْطَعْش شَخْص… وما في قاعَة؟
>
> **4.80–10.90 s** · مع ProHost، الـ conference room بِسِعْر عَ الشَّخْص: بِتْحُط العَدَد، بْتْزيد coffee break، وبْتِبْعَت الـ request.
>
> **11.15–15.90 s** · نَزِّل ProHost، الـ booking بِبَلاش… وإذا عِنْدَك space، أَوَّل Premium بْنُصّ السِّعِر.

**Caption:**
You're a trainer with 18 people on Saturday and no proper room. 🎤
With ProHost, conference rooms are priced per seat: set the headcount, add a coffee break and send the request.
📲 Download ProHost on Google Play. Booking is free.
🎉 Own a space? 50% off your first ProHost Premium subscription, monthly or yearly.
**Hashtags:** #CorporateTraining #Workshop #ConferenceRoom #TrainingRoom #Lebanon #ProHost #Events

---

## You're a life coach (by the hour) (`final/post5_lifecoach_final.mp4`)
**Background:** cozy coaching room. **On screen:** "You're a life coach. Two sessions today. Why pay a month's rent?" → "Finally, book by the hour" → Download ProHost + offer.

**Voiceover (Lebanese Arabic):**
**Voice:** `Umbriel` (male) · file `voice/post5_lifecoach_voice.mp3`

> **0.30–4.55 s** · إنتَ life coach، وعِنْدَك جَلْسْتَيْن اليوم… ليش تِدْفَع إيجار شَهِر؟
>
> **4.80–10.90 s** · أَخيراً فيك تِحْجُز room بِالساعَة: بْتْشوف شو فاضي، بِتْنَقّي الساعَة، وخَلْصِت.
>
> **11.15–15.90 s** · نَزِّل ProHost، الـ booking بِبَلاش… وإذا عِنْدَك space، أَوَّل Premium بْنُصّ السِّعِر.

**Caption:**
You're a life coach with two sessions today. Why pay a month's rent? ✨
Finally, book a quiet room by the hour, with live availability across Beirut.
📲 Download ProHost on Google Play. Booking is free.
🎉 Own a space? 50% off your first ProHost Premium subscription, monthly or yearly.
**Hashtags:** #LifeCoach #CoachingLebanon #Beirut #HourlyOffice #Freelance #ProHost #WorkFlexibly

---

## You're a dietitian (monthly) (`final/post6_dietitian_final.mp4`)
**Background:** nutrition clinic. **On screen:** "You're a dietitian. Ready for your own clinic. Not for a long lease." → "With ProHost, rent month to month" → Download ProHost + offer.

**Voiceover (Lebanese Arabic):**
**Voice:** `Callirrhoe` (female) · file `voice/post6_dietitian_voice.mp3`

> **0.30–4.55 s** · إنتَ dietitian وجاهِز لَـ clinic إِلَك… بَس بلا عَقْد طَويل؟
>
> **4.80–10.90 s** · مع ProHost بْتِسْتَأْجِر clinic جاهْزِة شَهِر بِشَهِر، وبْتْكَبِّر وَئْت ما بَدَّك.
>
> **11.15–15.90 s** · نَزِّل ProHost، الـ booking بِبَلاش… وإذا عِنْدَك space، أَوَّل Premium بْنُصّ السِّعِر.

**Caption:**
You're a dietitian, ready for your own clinic but not for a long lease. 🥗
With ProHost, rent a ready clinic month to month and extend whenever you grow.
📲 Download ProHost on Google Play. Booking is free.
🎉 Own a space? 50% off your first ProHost Premium subscription, monthly or yearly.
**Hashtags:** #Dietitian #NutritionLebanon #ClinicForRent #PrivateClinic #HealthcareLebanon #ProHost #MonthlyRental

---

## You run a hotel (meeting rooms per attendee) (`final/post7_hotel_final.mp4`)
**Background:** hotel conference room. **On screen:** "You run a hotel. Your meeting rooms sit empty most weekdays." → "With ProHost, fill them" (request → Accept → monthly earnings) → Download ProHost + 50% off.

**Voiceover (Lebanese Arabic):**
**Voice:** `Charon` (male) · file `voice/post7_hotel_voice.mp3`

> **0.30–4.55 s** · عِنْدَك hotel، والـ meeting rooms فاضْيِة أَغْلَب أيّام الجِمْعَة؟
>
> **4.80–10.90 s** · مع ProHost، بْتْأَجِّرُن بِاليوم أو عَ الشَّخْص، وبْتِقْبَل الـ requests بِكَبْسِة.
>
> **11.15–15.90 s** · نَزِّل ProHost، وأَوَّل اشْتِراك Premium بْنُصّ السِّعِر، monthly أو yearly.

**Caption:**
You run a hotel and your meeting rooms sit empty most weekdays. 🏨
With ProHost, rent them by the day or per attendee and accept requests in a tap.
🎉 Launch offer: 50% off your first ProHost Premium subscription, monthly or yearly.
📲 Download ProHost on Google Play, link in bio.
**Hashtags:** #Hotels #HotelLebanon #MeetingRooms #ConferenceRoom #Beirut #HospitalityBusiness #ProHost

---

## You run a medical center (by the shift) (`final/post8_medical_final.mp4`)
**Background:** medical center corridor. **On screen:** "You run a medical center. Your clinic rooms are free every afternoon." → "Now you can rent them by the shift" → Download ProHost + 50% off.

**Voiceover (Lebanese Arabic):**
**Voice:** `Achernar` (female) · file `voice/post8_medical_voice.mp3`

> **0.30–4.55 s** · عِنْدَك medical center، والـ clinics فاضْيِة كِل بَعْد الضُّهِر؟
>
> **4.80–10.90 s** · هَلَّأ فيك تْأَجِّرُن بِالـ shift لَدَكاتْرِة وأَخِصّائِيّين، وإنتَ بْتْوافِق عَ كِل request.
>
> **11.15–15.90 s** · نَزِّل ProHost، وأَوَّل اشْتِراك Premium بْنُصّ السِّعِر، monthly أو yearly.

**Caption:**
You run a medical center and your clinic rooms are free every afternoon. 🩺
Now you can rent them by the shift to doctors and specialists, and approve every request yourself.
🎉 Launch offer: 50% off your first ProHost Premium subscription, monthly or yearly.
📲 Download ProHost on Google Play, link in bio.
**Hashtags:** #MedicalCenter #Clinic #ClinicForRent #HealthcareLebanon #Doctors #ProHost #Lebanon

---

## You run a training center (per attendee) (`final/post9_institute_final.mp4`)
**Background:** classroom. **On screen:** "You run a training center. Your classrooms are empty every evening and weekend." → "Finally, they can earn" → Download ProHost + 50% off.

**Voiceover (Lebanese Arabic):**
**Voice:** `Iapetus` (male) · file `voice/post9_institute_voice.mp3`

> **0.30–4.55 s** · عِنْدَك training center، والصُّفوف فاضْيِة كِل مَسا وكِل weekend؟
>
> **4.80–10.90 s** · مع ProHost، بْتْأَجِّرُن بِالساعَة أو عَ الشَّخْص، والـ requests بْتوصَلَك عَ الـ app.
>
> **11.15–15.90 s** · نَزِّل ProHost، وأَوَّل اشْتِراك Premium بْنُصّ السِّعِر، monthly أو yearly.

**Caption:**
You run a training center and your classrooms are empty every evening and weekend. 🎓
Finally, they can earn: rent them by the hour or per attendee, with every request in the app.
🎉 Launch offer: 50% off your first ProHost Premium subscription, monthly or yearly.
📲 Download ProHost on Google Play, link in bio.
**Hashtags:** #TrainingCenter #Classroom #Education #Workshops #ProHost #Lebanon

---

## You run a yoga studio (by the hour) (`final/post10_studio_final.mp4`)
**Background:** sunlit yoga studio. **On screen:** "You run a yoga studio. It's quiet until 6 PM." → "Now your free hours can pay" → Download ProHost + 50% off.

**Voiceover (Lebanese Arabic):**
**Voice:** `Vindemiatrix` (female) · file `voice/post10_studio_voice.mp3`

> **0.30–4.55 s** · عِنْدَك yoga studio، وما بْيِمْتِلي قَبِل السِّتّة؟
>
> **4.80–10.90 s** · أَخيراً فيك تْأَجِّر الساعات الفاضْيِة لَـ instructors كِل جِمْعَة، مع ProHost.
>
> **11.15–15.90 s** · نَزِّل ProHost، وأَوَّل اشْتِراك Premium بْنُصّ السِّعِر، monthly أو yearly.

**Caption:**
You run a yoga studio and it's quiet until 6 PM. 🧘
Now your free hours can pay: instructors book them by the hour, every week, on ProHost.
🎉 Launch offer: 50% off your first ProHost Premium subscription, monthly or yearly.
📲 Download ProHost on Google Play, link in bio.
**Hashtags:** #YogaStudio #Pilates #FitnessStudio #Beirut #StudioRental #ProHost #Lebanon

---

## Posting plan
1. **Week 1, space owners:** Post 1 (general), then the hotel, medical center, training center and yoga studio, one a day.
   Boost them to business and property owners in Lebanon.
2. **Week 2, professionals:** Post 2 (the app), then the psychologist, trainer, life coach and dietitian.
3. **Format:** share each one as a Reel and choose its `_cover.png` as the cover. Pin Post 1 and Post 2 to the profile.
4. **Link in bio:** the Google Play link or `pro-host.tech`. For promo campaigns, use `pro-host.tech/redeem?code=YOURCODE`.
