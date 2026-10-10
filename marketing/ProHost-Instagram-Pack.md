# ProHost — Instagram launch pack (6 animated posts)

Each video is 1080×1350 (4:5 feed), 15 s at 30 fps, H.264 MP4, silent. Add voice and music when you post.
The prices in the persona posts are illustrative; the end card says "Prices shown are examples".

**How to add the Lebanese voice**
- **Best:** have a Lebanese voice actor record each script below. Each script is timed to the video's scenes, so
  read at a calm pace. Then merge:
  `ffmpeg -i post1_hosts.mp4 -i voice1.m4a -c:v copy -c:a aac -b:a 192k -shortest post1_final.mp4`
- **AI voice:** Microsoft Azure Speech has Lebanese Arabic voices (`ar-LB-LaylaNeural`, female;
  `ar-LB-RamiNeural`, male). Paste the script, export MP3, merge as above. ElevenLabs also does Arabic, with
  a less Lebanese accent.
- **No tools:** open the video in Instagram › Reels editor › Voiceover, and record the script on your phone.
- **Music:** pick a soft, upbeat track from Instagram's library at 15–20% volume under the voice.

---

## Post 1 — For space owners (`post1_hosts.mp4`)
**Scenes:**
- 0–3.5 s: "Office. Clinic. Studio. Meeting room. Sitting empty?"
- 3.5–8 s: "Rent it on your terms": the rental types, then a week calendar filling with bookings.
- 8–11.5 s: "Run it all from one app": 4 features.
- 11.5–15 s: **50% OFF your first month of ProHost Premium**, the logo and Google Play.

**Voiceover (Lebanese Arabic):**
> (0–3.5) عندك مكتب، عيادة، استوديو أو صالة اجتماعات… قاعدة فاضية؟
> (3.5–8) مع ProHost أجّرها عذوقك: بالساعة، بالشيفت، باليوم، بالشهر أو عَ عدد الأشخاص.
> (8–11.5) كل الطلبات بمحل واحد، المواعيد مضبوطة بلا حجز مزدوج، وبتغيّر سعرك وقت ما بدّك.
> (11.5–15) وهلّق، أوّل شهر من ProHost Premium بنصّ السعر. نزّل ProHost من Google Play.

**Caption:**
Your office, clinic or meeting room doesn't have to sit empty. 🗝️
With ProHost you rent it your way: by the hour, the shift, the day, the month, or per attendee.
Requests land in one inbox, availability updates live, and you can change prices anytime.
🎉 Launch offer: 50% off your first month of ProHost Premium.
📲 Get ProHost on Google Play, link in bio.
**Hashtags:** #ProHost #Lebanon #Beirut #OfficeSpace #ClinicForRent #MeetingRoom #CoworkingLebanon #PropertyOwners #FlexibleWorkspace #PassiveIncome

---

## Post 2 — The app (`post2_app.mp4`)
**Scenes:**
- 0–3 s: "Workspace, on your terms." The phone rises showing the sign-in screen.
- 3–6.3 s: the Explore map, with callouts "Live map of spaces" and "Hour, shift, day, month".
- 6.3–9.6 s: the neighbourhood map, with "Real-time availability" and "Talk on WhatsApp".
- 9.6–12.4 s: host Financials, with "Hosts track it all".
- 12.4–15 s: logo, "Find it. Book it. Get to work." and Google Play.

**Voiceover:**
> (0–3) شغلك… بشروطك.
> (3–6.3) شوف كل المساحات عالخريطة، عيادات، مكاتب، استوديوهات، واحجز بالساعة، بالشيفت، باليوم أو بالشهر.
> (6.3–9.6) بتعرف شو فاضي هلّق، وبتحكي صاحب المحل مباشرة عالواتساب.
> (9.6–12.4) ولأصحاب المساحات، كل الطلبات والحجوزات والمدخول قدّامك.
> (12.4–15) ProHost… لاقيها، احجزها، وبلّش شغل. ببلاش للمهنيين، عَ Google Play.

**Caption:**
Lebanon's flexible workspace marketplace is here. 🇱🇧
🗺️ A live map of clinics, offices, studios and halls
⏱️ Book by the hour, shift, day or month, or per attendee
✅ Real-time availability, so no back-and-forth
💬 Talk to the host directly on WhatsApp
Free for professionals. Download ProHost on Google Play.
**Hashtags:** #ProHost #Lebanon #Beirut #Workspace #RentAnOffice #ClinicSpace #Startups #Freelancers #Therapists #LebanonBusiness

---

## Post 3 — Dr. Maya, clinical psychologist (shift-based) (`post3_psychologist.mp4`)
**Persona:** Dr. Maya Haddad, Achrafieh. She sees clients two mornings a week and won't pay for a clinic all week.

**Voiceover:**
> (0–4.6) أنا مايا، معالِجة نفسية بالأشرفية. ما بحتاج عيادة كل الجمعة… بس التلاتا والخميس الصبح.
> (4.6–11) عَ ProHost بختار الشيفت الصبحي، التلاتا والخميس، لأربع جمع… والطلب بيوصل لصاحب العيادة، وبيرد عليّ عالواتساب.
> (11–15) صار عندي عيادتي، يومين بالجمعة، وبدفع بس عالوقت يلّي بشتغل فيه. ProHost.

**Caption:**
Meet Dr. Maya. 🧠 She needs a calm therapy room on Tuesday and Thursday mornings, not a full-time lease.
On ProHost she books clinics by the shift and pays only for the hours she sees clients.
Psychologists, therapists, counsellors: your practice, your schedule.
📲 ProHost on Google Play.
**Hashtags:** #Psychologist #TherapistLebanon #PrivatePractice #ClinicRental #Achrafieh #Beirut #ProHost #MentalHealthLebanon

---

## Post 4 — Karim, corporate trainer (per attendee) (`post4_trainer.mp4`)
**Persona:** Karim Nassar, Dbayeh. He has 18 trainees on Saturday and needs a proper room.

**Voiceover:**
> (0–4.6) أنا كريم، مدرّب بالشركات. عندي تمنطعش متدرّب السبت، وبدّي قاعة محترمة… مش قهوة.
> (4.6–11) عَ ProHost، القاعة مسعّرة عَ الشخص: بحط تمنطعش، بختار الكوفي بريك، وببعت الطلب.
> (11–15) تمنطعش كرسي… بكبسة وحدة. نزّل ProHost.

**Caption:**
Meet Karim. 🎤 18 trainees on Saturday deserve better than a noisy café.
On ProHost, training and conference rooms are priced per attendee: set your headcount, add extras, send the request.
Trainers, workshop hosts, team leads: book the room in a minute.
📲 ProHost on Google Play.
**Hashtags:** #CorporateTraining #Workshop #ConferenceRoom #TrainingRoom #Dbayeh #Lebanon #ProHost #Events

---

## Post 5 — Rana, life coach (hourly) (`post5_lifecoach.mp4`)
**Persona:** Rana Khoury, Hamra. She has two coaching sessions today and won't pay for a month.

**Voiceover:**
> (0–4.6) أنا رنا، لايف كوتش بالحمرا. عندي جلستين اليوم… ليش بدّي إدفع إيجار شهر كامل؟
> (4.6–11) بفتح ProHost، بشوف شو فاضي هلّق، بختار الساعة أربعة وخمسة… وخلصت.
> (11–15) احجز بالساعة، واشتغل من وين ما بدّك. ProHost.

**Caption:**
Meet Rana. ✨ Two client sessions today, so why pay for a whole month?
On ProHost she books a quiet room by the hour, right when she needs it, and sees live availability across Beirut.
Coaches, consultants, tutors: book the hour, not the lease.
📲 ProHost on Google Play.
**Hashtags:** #LifeCoach #CoachingLebanon #Hamra #Beirut #HourlyOffice #Freelance #ProHost #WorkFlexibly

---

## Post 6 — Dr. Nour, dietitian (monthly) (`post6_dietitian.mp4`)
**Persona:** Dr. Nour Assaf, Jounieh. She's ready for her own clinic, without a three-year lease.

**Voiceover:**
> (0–4.6) أنا نور، أخصائية تغذية بجونيه. جاهزة لعيادتي الخاصة… بس بلا عقد تلات سنين.
> (4.6–11) عَ ProHost بلاقي عيادة جاهزة، بختار تلات شهور، وببلّش أوّل تشرين التاني.
> (11–15) عيادتك الخاصة… شهر بشهر، بلا عقد طويل. ProHost.

**Caption:**
Meet Dr. Nour. 🥗 Ready for her own clinic, but not for a three-year lease.
On ProHost she rents a ready clinic month to month: start when you're ready, extend when you grow.
Dietitians, physios, doctors: your own space, on your terms.
📲 ProHost on Google Play.
**Hashtags:** #Dietitian #NutritionLebanon #Jounieh #ClinicForRent #PrivateClinic #HealthcareLebanon #ProHost #MonthlyRental

---

## Posting plan
1. **Day 1:** Post 2 (the app). It introduces the brand.
2. **Day 2:** Post 1 (space owners plus the 50% offer). Boost it to property and business-owner audiences in Lebanon.
3. **Days 3–6:** Posts 3–6, one per day.
4. **Format:** share each as a **Reel** (4:5 is accepted) and pin Posts 1 and 2 to the profile.
5. **Link in bio:** the Google Play link, or `pro-host.tech`. For promo campaigns, use `pro-host.tech/redeem?code=YOURCODE`.
