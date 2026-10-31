/** Pure helpers of the human conversation flow: ending the chat, silence follow-ups, language. */

/** One gentle nudge 3 h after the person went quiet; no goodbye after it (owner's choice: the chat stays open). */
export const NUDGE_AFTER_MS = 3 * 3_600_000;
/** Meta only lets us message inside 24 h of their last message; stay well inside it. */
export const WINDOW_MS = 23 * 3_600_000;

const END_WORDS = new Set([
  "thanks", "thank", "you", "thx", "ty", "tysm", "ok", "okay", "okey", "k", "kk", "bye", "goodbye", "cya", "see", "ya", "later", "got", "it", "great", "perfect", "cool", "nice", "awesome", "good", "alright", "much", "so", "very", "a", "lot", "appreciate", "sure", "noted", "done", "np",
  "merci", "beaucoup", "dacc", "daccord", "d'accord", "revoir", "au", "bien", "compris", "super", "parfait",
  "gracias", "vale", "adios", "adiós", "chao", "chau", "entendido", "genial", "muchas",
  "شكرا", "شكراً", "شكرًا", "مشكور", "ممنون", "يسلمو", "تسلم", "تمام", "اوكي", "أوكي", "اوك", "أوك", "باي", "سلام", "مع", "السلامة", "ماشي", "حاضر", "كتير", "الله", "يعطيك", "العافية", "يعطيكم", "وفهمت", "فهمت",
]);

const norm = (t: string) => t.toLowerCase().replace(/[^\p{L}\p{M}\s']/gu, " ").replace(/\s+/g, " ").trim();

// Words that actually close a chat (thanks / bye); "ok", "sure", "cool" alone are acknowledgements, not goodbyes.
const CLOSERS = new Set([
  "thanks", "thank", "thx", "ty", "tysm", "appreciate", "bye", "goodbye", "cya", "later", "merci", "revoir", "gracias", "adios", "adiós", "chao", "chau",
  "شكرا", "شكراً", "شكرًا", "مشكور", "ممنون", "يسلمو", "تسلم", "باي", "السلامة", "العافية",
]);
const BYES = new Set(["bye", "goodbye", "cya", "later", "revoir", "adios", "adiós", "chao", "chau", "باي", "السلامة"]);

/**
 * "thanks", "thank you so much 🙏", "got it, thanks", "شكراً", "bye": the person is wrapping up.
 * A bare "ok" / "sure" / "cool" is an answer, not a goodbye. Right after we asked something (`awaiting` "reply"),
 * only an explicit goodbye ("thanks, bye") ends the chat; "thanks" alone is just politeness there.
 */
export function isExitIntent(text: string, awaiting = ""): boolean {
  const words = norm(text).split(" ").filter(Boolean);
  if (!words.length || words.length > 6 || !words.every((w) => END_WORDS.has(w))) return false;
  if (!words.some((w) => CLOSERS.has(w))) return false;
  return awaiting !== "reply" || words.some((w) => BYES.has(w));
}

/** "no", "nope", "no thanks", "لا": the answer to "type no if you are new". */
export function isNo(text: string): boolean {
  const n = norm(text);
  const words = n.split(" ").filter(Boolean);
  if (!words.length || words.length > 4) return false;
  return /^(no|nope|nah|non|لا|لأ|la|nel)$/.test(words[0]) || /^(new|i'?m new|im new|not yet)$/.test(n);
}

const EMAIL_RE = /[^\s@<>"',;()]{1,64}@[^\s@<>"',;()]{1,120}\.[A-Za-z]{2,24}/;
export function findEmail(text: string): string | null {
  const m = EMAIL_RE.exec(text);
  return m ? m[0].toLowerCase().replace(/[.]+$/, "") : null;
}

export type Lang = "en" | "ar" | "fr" | "es";
export function detectLang(text: string, fallback: string): Lang {
  if (/[؀-ۿ]/.test(text)) return "ar";
  const f = fallback.slice(0, 2).toLowerCase();
  return f === "ar" || f === "fr" || f === "es" ? f : "en";
}

const firstName = (name?: string) => (name ?? "").trim().split(/\s+/)[0] ?? "";

const NUDGE: Record<Lang, (n: string, topic: string) => string> = {
  en: (n, t) => `${n ? n + ", just" : "Just"} checking in${t ? `: still looking for ${t}? I can send you a few options whenever you're ready.` : ". Anything else I can help you with?"}`,
  ar: (n, t) => `${n ? n + "، " : ""}حبيت أطمّن عليك${t ? `: لسا عم تدوّر على ${t}؟ فيني بعتلك كم خيار ساعة ما تكون جاهز.` : ". في شي تاني فيني ساعدك فيه؟"}`,
  fr: (n, t) => `${n ? n + ", je" : "Je"} reviens vers toi${t ? ` : tu cherches toujours ${t} ? Je peux t'envoyer quelques options quand tu veux.` : ". Je peux t'aider pour autre chose ?"}`,
  es: (n, t) => `${n ? n + ", solo" : "Solo"} quería saber de ti${t ? `: ¿sigues buscando ${t}? Te puedo enviar algunas opciones cuando quieras.` : ". ¿Te ayudo con algo más?"}`,
};
const KIND_WORDS: Record<string, Record<Lang, string>> = {
  clinic: { en: "a clinic room", ar: "عيادة", fr: "un cabinet", es: "un consultorio" },
  therapy: { en: "a therapy room", ar: "غرفة علاج", fr: "une salle de thérapie", es: "una sala de terapia" },
  office: { en: "an office", ar: "مكتب", fr: "un bureau", es: "una oficina" },
  desk: { en: "a desk", ar: "مكتب مشترك", fr: "un poste de travail", es: "un escritorio" },
  meeting: { en: "a meeting room", ar: "قاعة اجتماعات", fr: "une salle de réunion", es: "una sala de reuniones" },
  training: { en: "a training room", ar: "قاعة تدريب", fr: "une salle de formation", es: "una sala de formación" },
  studio: { en: "a studio", ar: "استوديو", fr: "un studio", es: "un estudio" },
  gym: { en: "a sports space", ar: "مساحة رياضية", fr: "un espace sportif", es: "un espacio deportivo" },
  space: { en: "a space", ar: "مساحة", fr: "un espace", es: "un espacio" },
};
const IN: Record<Lang, string> = { en: " in ", ar: " ب", fr: " à ", es: " en " };
/** "a clinic room in Beirut" from the search the person was building (empty when nothing is known yet). */
export function nudgeTopic(lang: Lang, kind?: string, city?: string): string {
  if (!kind && !city) return "";
  const k = (KIND_WORDS[kind ?? ""] ?? KIND_WORDS.space)[lang];
  return city ? `${k}${IN[lang]}${city}` : k;
}

const CLOSE_THANKS: Record<Lang, (n: string) => string> = {
  en: (n) => `Thank you${n ? " " + n : ""}! I'm ProHost's automated assistant and I'm here whenever you need anything. Take care!`,
  ar: (n) => `شكراً${n ? " " + n : ""}! أنا المساعد الآلي لـ ProHost وأنا هنا متى احتجت أي شيء. اعتنِ بنفسك!`,
  fr: (n) => `Merci${n ? " " + n : ""} ! Je suis l'assistant automatique de ProHost et je reste là si besoin. Prends soin de toi !`,
  es: (n) => `¡Gracias${n ? " " + n : ""}! Soy el asistente automático de ProHost y aquí estoy para lo que necesites. ¡Cuídate!`,
};

export const closingThanks = (lang: Lang, name?: string) => CLOSE_THANKS[lang](firstName(name));

export interface WaitingThread {
  dueAt?: number; nudges?: number; lang?: string; name?: string; lastUserAt?: number; closed?: boolean;
  state?: { search?: { kind?: string; city?: string } };
}
export type FollowUp = { action: "nudge"; text: string } | null;

/** What to do when a waiting thread comes due: ONE nudge about what they were looking for, never twice, never in a closed
 *  chat and never past Meta's 24 h window. There is no goodbye message: the chat simply stays open. */
export function nextFollowUp(t: WaitingThread, now: number): FollowUp {
  if (t.closed || !t.dueAt || now < t.dueAt || (t.nudges ?? 0) >= 1) return null;
  if (!t.lastUserAt || now - t.lastUserAt > WINDOW_MS) return null;
  const lang = detectLang("", t.lang ?? "en");
  return { action: "nudge", text: NUDGE[lang](firstName(t.name), nudgeTopic(lang, t.state?.search?.kind, t.state?.search?.city)) };
}
