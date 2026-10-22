/** Pure helpers of the human conversation flow: ending the chat, silence follow-ups, language. */

export const NUDGE_AFTER_MS = 2 * 60_000;
export const CLOSE_AFTER_MS = 2 * 60_000;
/** Meta only lets us message inside 24 h of their last message; stay well inside it. */
export const WINDOW_MS = 23 * 3_600_000;

const END_WORDS = new Set([
  "thanks", "thank", "you", "thx", "ty", "tysm", "ok", "okay", "okey", "k", "kk", "bye", "goodbye", "cya", "see", "ya", "later", "got", "it", "great", "perfect", "cool", "nice", "awesome", "good", "alright", "much", "so", "very", "a", "lot", "appreciate", "sure", "noted", "done", "np",
  "merci", "beaucoup", "dacc", "daccord", "d'accord", "revoir", "au", "bien", "compris", "super", "parfait",
  "gracias", "vale", "adios", "adiós", "chao", "chau", "entendido", "genial", "muchas",
  "شكرا", "شكراً", "شكرًا", "مشكور", "ممنون", "يسلمو", "تسلم", "تمام", "اوكي", "أوكي", "اوك", "أوك", "باي", "سلام", "مع", "السلامة", "ماشي", "حاضر", "كتير", "الله", "يعطيك", "العافية", "يعطيكم", "وفهمت", "فهمت",
]);

const norm = (t: string) => t.toLowerCase().replace(/[^\p{L}\p{M}\s']/gu, " ").replace(/\s+/g, " ").trim();

/** "thanks", "ok", "bye", "thank you so much 🙏", "شكراً"... : nothing left to answer, the person is wrapping up. */
export function isExitIntent(text: string): boolean {
  const words = norm(text).split(" ").filter(Boolean);
  if (!words.length || words.length > 5) return false;
  return words.every((w) => END_WORDS.has(w));
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

const NUDGE: Record<Lang, (n: string) => string> = {
  en: (n) => `${n ? n + ", are" : "Are"} you still there? I'm happy to help whenever you're ready.`,
  ar: (n) => `${n ? n + "، " : ""}هل ما زلت معي؟ يسعدني أن أساعدك متى كنت جاهزاً.`,
  fr: (n) => `${n ? n + ", es" : "Es"}-tu toujours là ? Je suis là dès que tu es prêt.`,
  es: (n) => `${n ? n + ", ¿s" : "¿S"}igues por ahí? Estoy aquí cuando quieras.`,
};
const CLOSE_BUSY: Record<Lang, string> = {
  en: "I see you're busy, pleasure to assist you another time. Take care!",
  ar: "أراك مشغولاً، سعدت بمساعدتك وفي أي وقت آخر. اعتنِ بنفسك!",
  fr: "Je vois que tu es occupé, ce fut un plaisir de t'aider, à une prochaine fois. Prends soin de toi !",
  es: "Veo que estás ocupado, un placer ayudarte en otro momento. ¡Cuídate!",
};
const CLOSE_THANKS: Record<Lang, (n: string) => string> = {
  en: (n) => `Thank you${n ? " " + n : ""}! I have to go now to help others waiting for me, but I'm always here if you need anything. Take care!`,
  ar: (n) => `شكراً${n ? " " + n : ""}! علي أن أذهب الآن لمساعدة أشخاص آخرين بانتظاري، وأنا دائماً هنا إن احتجت أي شيء. اعتنِ بنفسك!`,
  fr: (n) => `Merci${n ? " " + n : ""} ! Je dois y aller pour aider d'autres personnes qui m'attendent, mais je reste là si besoin. Prends soin de toi !`,
  es: (n) => `¡Gracias${n ? " " + n : ""}! Tengo que irme a ayudar a otras personas que me esperan, pero aquí estoy para lo que necesites. ¡Cuídate!`,
};

export const closingThanks = (lang: Lang, name?: string) => CLOSE_THANKS[lang](firstName(name));

export interface WaitingThread { dueAt?: number; nudges?: number; lang?: string; name?: string; lastUserAt?: number; closed?: boolean }
export type FollowUp = { action: "nudge" | "close"; text: string } | null;

/** What to do when a waiting thread comes due. One nudge, then a polite goodbye; never inside a closed chat or past Meta's window. */
export function nextFollowUp(t: WaitingThread, now: number): FollowUp {
  if (t.closed || !t.dueAt || now < t.dueAt) return null;
  if (t.lastUserAt && now - t.lastUserAt > WINDOW_MS) return null;
  const lang = detectLang("", t.lang ?? "en");
  if (!t.nudges) return { action: "nudge", text: NUDGE[lang](firstName(t.name)) };
  return { action: "close", text: CLOSE_BUSY[lang] };
}
