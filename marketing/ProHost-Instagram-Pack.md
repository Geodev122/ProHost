# ProHost — Instagram launch pack (6 animated Reels)

Each video is **1080×1920 (9:16 Reel)**, 15 s at 30 fps, H.264 MP4, silent; the voice is a separate file.
All content sits inside the centre 1080×1350 area (y 285–1635), so it stays clear of Instagram's Reel UI and
also crops cleanly to a 4:5 feed post. The personas are anonymous: each introduces themself only by
profession and area ("Hello, I'm a…"). Prices in the persona posts are illustrative; the end card says so.

**Voiceovers** (`voice/`, a different voice for each post, Google Cloud Text-to-Speech):
- `<post>_voice.mp3` / `.wav`: the full 15.0 s track. Every line already starts on its scene, and the track is levelled to -16 LUFS.
- `lines/<post>_NN.wav`: each line on its own, in case you want to re-time one.
- `report.json`: the engine, the start and end of each line, and any speed-up applied.
- **Merge:** `ffmpeg -i post1_hosts.mp4 -i voice/post1_hosts_voice.mp3 -c:v copy -c:a aac -b:a 192k -shortest post1_final.mp4`.
  CapCut or Instagram's editor also work: line the audio up at 0:00. Add music, a soft track at 15–20 % volume under the voice.
- **Voice engine:** Gemini-TTS (gemini-2.5-pro-tts), told to speak Lebanese dialect and to follow the acting notes under
  each line. If a take runs long, up to three are recorded and the shortest is kept.
- **Finished videos with voice:** `final/<post>_final.mp4`.
- **Accent:** the lines are written in Lebanese dialect and the voice is told to speak Lebanese, but an AI voice can still
  sound a bit general Levantine. For the most local sound, a Lebanese voice actor can read the same scripts with the
  timings below.
- **Regenerate:** edit `scripts/marketing/voiceover.json`, then run Actions › "Marketing voiceovers (manual)".

---

## Post 1 — For space owners (`post1_hosts.mp4`)
**Scenes:**
- 0–3.5 s: "Office. Clinic. Studio. Meeting room. Sitting empty?"
- 3.7–8 s: "Rent it on your terms": the rental types, then a week calendar filling with bookings.
- 8.3–11.5 s: "Run it all from one app", with 4 features.
- 11.6–15 s: **50% OFF your first month of ProHost Premium**, the logo and Google Play.

**Voiceover (Lebanese Arabic)**: voice `Orus` (male), confident, warm Lebanese businessman in his 40s talking to fellow property owners.  
File: `voice/post1_hosts_voice.mp3` (15.0 s, each line already starts on its scene)

> **0.25–3.55 s** · عندك مكتب أو عيادة… قاعدة فاضية؟  
> _Curious and slightly teasing, a knowing half-smile, rising question at the end._
>
> **3.80–8.05 s** · مع برو هوست أجّرها عَ ذوقك: بالساعة، باليوم، بالشهر، أو عَ الشخص.  
> _Confident and upbeat, listing the options with rhythm._
>
> **8.40–11.40 s** · كل الطلبات بمحل واحد، وبتغيّر سعرك وقت ما بدّك.  
> _Reassuring and calm, like a friend who has it all under control._
>
> **11.65–14.90 s** · وهلّق، أوّل شهر بنصّ السعر! نزّل برو هوست.  
> _Excited, celebratory announcement, big smile._

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
- 0–3 s: "Workspace, on your terms." The phone rises.
- 3–6.3 s: the Explore map, with callouts.
- 6.3–9.6 s: the neighbourhood map, with "Real-time availability" and "Talk on WhatsApp".
- 9.6–12.4 s: the host's Financials screen.
- 12.4–15 s: the logo, "Find it. Book it. Get to work." and Google Play.

**Voiceover (Lebanese Arabic)**: voice `Aoede` (female), warm, modern Lebanese woman in her late 20s presenting an app she loves.  
File: `voice/post2_app_voice.mp3` (15.0 s, each line already starts on its scene)

> **0.35–2.95 s** · شغلك… بشروطك.  
> _Slow, confident, inviting, with a small pause between the two words._
>
> **3.15–6.25 s** · كل المساحات عالخريطة، واحجز بالساعة أو بالشهر.  
> _Bright and enthusiastic, discovering the map._
>
> **6.45–9.55 s** · بتعرف شو فاضي هلّق، وبتحكي صاحب المحل عالواتساب.  
> _Easy, friendly, matter-of-fact._
>
> **9.75–12.35 s** · ولأصحاب المساحات، كل شي قدّامك.  
> _Professional and reassuring, speaking to space owners._
>
> **12.60–14.95 s** · برو هوست: لاقيها، احجزها، واشتغل.  
> _Punchy and proud slogan, energy rising on the last phrase._

**Caption:**
Lebanon's flexible workspace marketplace is here. 🇱🇧
🗺️ A live map of clinics, offices, studios and halls
⏱️ Book by the hour, shift, day or month, or per attendee
✅ Real-time availability, so no back-and-forth
💬 Talk to the host directly on WhatsApp
Free for professionals. Download ProHost on Google Play.
**Hashtags:** #ProHost #Lebanon #Beirut #Workspace #RentAnOffice #ClinicSpace #Startups #Freelancers #Therapists #LebanonBusiness

---

## Post 3 — A clinical psychologist, booking by the shift (`post3_psychologist.mp4`)
**On screen:** "Hello, I'm a clinical psychologist in Achrafieh." She sees clients two mornings a week and won't pay for a clinic all week.

**Voiceover (Lebanese Arabic)**: voice `Achernar` (female), calm, gentle clinical psychologist in her 30s; soft and thoughtful, never salesy.  
File: `voice/post3_psychologist_voice.mp3` (15.0 s, each line already starts on its scene)

> **0.30–4.55 s** · مرحبا، أنا معالِجة نفسية بالأشرفية. بدّي عيادة يومين بالجمعة بس.  
> _Soft, warm introduction, a little tired of paying for a full-time clinic._
>
> **4.80–10.90 s** · عَ برو هوست بختار الشيفت الصبحي، التلاتا والخميس، لأربع جمع… وببعت الطلب.  
> _Relaxed and pleased, explaining how simple it was, gentle pace._
>
> **11.15–14.90 s** · صار عندي عيادتي، يومين بالجمعة. برو هوست.  
> _Quietly happy and fulfilled, a soft smile in the voice._

**Caption:**
"I don't need a clinic all week. Just Tuesday and Thursday mornings." 🧠
On ProHost, psychologists book calm therapy rooms by the shift and pay only for the hours they see clients.
Psychologists, therapists, counsellors: your practice, your schedule.
📲 ProHost on Google Play.
**Hashtags:** #Psychologist #TherapistLebanon #PrivatePractice #ClinicRental #Achrafieh #Beirut #ProHost #MentalHealthLebanon

---

## Post 4 — A corporate trainer, booking per attendee (`post4_trainer.mp4`)
**On screen:** "Hello, I'm a corporate trainer in Dbayeh." He has 18 trainees on Saturday and needs a proper room.

**Voiceover (Lebanese Arabic)**: voice `Fenrir` (male), energetic corporate trainer in his 30s, dynamic and a little playful.  
File: `voice/post4_trainer_voice.mp3` (15.0 s, each line already starts on its scene)

> **0.30–4.55 s** · مرحبا، أنا مدرّب شركات. عندي تمنطعش متدرّب السبت… وما بدّي قهوة!  
> _Energetic, slightly exasperated about working from cafés, playful._
>
> **4.80–10.90 s** · عَ برو هوست القاعة مسعّرة عَ الشخص: بحط تمنطعش، بزيد كوفي بريك، وببعت الطلب.  
> _Fast, efficient, satisfied, like ticking boxes._
>
> **11.15–14.90 s** · تمنطعش كرسي… بكبسة وحدة! برو هوست.  
> _Triumphant and proud, a confident punchline._

**Caption:**
"18 trainees on Saturday. I need a real training room, not a café." 🎤
On ProHost, training and conference rooms are priced per attendee: set your headcount, add extras and send the request.
Trainers, workshop hosts, team leads: book the room in a minute.
📲 ProHost on Google Play.
**Hashtags:** #CorporateTraining #Workshop #ConferenceRoom #TrainingRoom #Dbayeh #Lebanon #ProHost #Events

---

## Post 5 — A life coach, booking by the hour (`post5_lifecoach.mp4`)
**On screen:** "Hello, I'm a life coach in Hamra." She has two coaching sessions today and won't pay for a month.

**Voiceover (Lebanese Arabic)**: voice `Leda` (female), bright, upbeat life coach in her late 20s, positive and motivating.  
File: `voice/post5_lifecoach_voice.mp3` (15.0 s, each line already starts on its scene)

> **0.30–4.55 s** · مرحبا، أنا لايف كوتش بالحمرا. عندي جلستين اليوم… ليش إدفع شهر؟  
> _Cheerful and a bit cheeky, the question is rhetorical._
>
> **4.80–10.90 s** · بفتح برو هوست، بشوف شو فاضي، بختار الساعة أربعة وخمسة… وخلصت!  
> _Light and quick, delighted at how easy it is, ending with a happy 'done'._
>
> **11.15–14.90 s** · احجز بالساعة، واشتغل من وين ما بدّك. برو هوست.  
> _Motivating and warm, speaking straight to the viewer._

**Caption:**
"Two client sessions today. Why pay for a whole month?" ✨
On ProHost, coaches book a quiet room by the hour, right when they need it, with live availability across Beirut.
Coaches, consultants, tutors: book the hour, not the lease.
📲 ProHost on Google Play.
**Hashtags:** #LifeCoach #CoachingLebanon #Hamra #Beirut #HourlyOffice #Freelance #ProHost #WorkFlexibly

---

## Post 6 — A dietitian, renting by the month (`post6_dietitian.mp4`)
**On screen:** "Hello, I'm a dietitian in Jounieh." She's ready for her own clinic, without a three-year lease.

**Voiceover (Lebanese Arabic)**: voice `Sulafat` (female), composed, ambitious dietitian in her 30s; warm but determined.  
File: `voice/post6_dietitian_voice.mp3` (15.0 s, each line already starts on its scene)

> **0.30–4.55 s** · مرحبا، أنا أخصائية تغذية بجونيه. بدّي عيادتي… بلا عقد طويل.  
> _Determined and hopeful, with a firm 'but' about the long lease._
>
> **4.80–10.90 s** · عَ برو هوست لقيت عيادة جاهزة، اخترت تلات شهور… وببلّش الشهر الجاي.  
> _Relieved and practical, it just worked._
>
> **11.15–14.90 s** · عيادتك الخاصة، شهر بشهر، بلا عقد طويل. برو هوست.  
> _Warm, confident invitation to the viewer._

**Caption:**
"I'm ready for my own clinic, without a three-year lease." 🥗
On ProHost you rent a ready clinic month to month: start when you're ready and extend as you grow.
Dietitians, physios, doctors: your own space, on your terms.
📲 ProHost on Google Play.
**Hashtags:** #Dietitian #NutritionLebanon #Jounieh #ClinicForRent #PrivateClinic #HealthcareLebanon #ProHost #MonthlyRental

---

## Posting plan
1. **Day 1:** Post 2 (the app). It introduces the brand.
2. **Day 2:** Post 1 (space owners plus the 50% offer). Boost it to property and business-owner audiences in Lebanon.
3. **Days 3–6:** Posts 3–6, one per day.
4. **Format:** share each as a **Reel** (native 9:16), use its `_cover.png` as the cover, and pin Posts 1 and 2 to the profile.
5. **Link in bio:** the Google Play link or `pro-host.tech`. For promo campaigns, use `pro-host.tech/redeem?code=YOURCODE`.
