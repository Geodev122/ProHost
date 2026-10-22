/** Pure intent classifier for public comments: no model, no I/O. Languages: English, Arabic, French, Spanish. */

export type CommentKind = "positive" | "question" | "negative" | "spam" | "neutral";
export type CommentLang = "en" | "ar" | "fr" | "es";

const norm = (s: string) => s.normalize("NFD").replace(/[\u0300-\u036f\u064b-\u065f\u0670]/g, "").toLowerCase();
const tokens = (s: string): string[] => norm(s).replace(/[^\p{L}\p{N}']+/gu, " ").trim().split(" ").filter(Boolean);
const EMOJI = /\p{Extended_Pictographic}/gu;

const POSITIVE_EMOJI = /[❤💙💚💛🧡💜🖤🤍💖💗💕💞💓😍🥰😘😊😁😃😄🙌👏👍🔥💯🙏✨🌟⭐👌🤩😎🎉🥳💪✅]/u;
const NEGATIVE_EMOJI = /[😡😠🤬👎🤮🤢💩🖕😤🙄😒]/u;

const POSITIVE_WORDS = new Set([
  "thanks", "thank", "thx", "ty", "nice", "great", "good", "love", "loved", "awesome", "amazing", "cool", "perfect", "wow", "congrats", "congratulations", "bravo", "beautiful", "excellent", "best", "super", "fantastic", "brilliant", "wonderful", "helpful", "useful", "impressive", "well",
  "merci", "genial", "génial", "magnifique", "parfait", "felicitations", "félicitations", "top", "gracias", "excelente", "bravisimo", "felicidades", "increible", "buenisimo",
  "شكرا", "شكراً", "شكرًا", "مشكور", "يعطيك", "العافية", "رائع", "ممتاز", "حلو", "حلوة", "جميل", "جميلة", "مبروك", "برافو", "تحفة", "الله", "يسلمو", "تسلم", "تسلمو", "ابداع", "إبداع", "مبدع", "كتير", "حلوه", "بطل",
]);
const NEGATIVE_WORDS = new Set([
  "scam", "scammer", "fake", "fraud", "worst", "terrible", "awful", "horrible", "hate", "trash", "garbage", "useless", "rubbish", "stupid", "shame", "disgusting", "liar", "lies", "spam", "rip", "ripoff", "waste", "sucks", "bad", "pathetic", "crap",
  "arnaque", "nul", "horrible", "mensonge", "menteur", "voleur", "pire", "estafa", "fraude", "basura", "mentira", "ladron", "ladrón", "horrible", "pesimo", "pésimo",
  "نصب", "نصاب", "حرامي", "كذاب", "كذب", "سيء", "سيئ", "زبالة", "فاشل", "احتيال", "مزور", "وهمي", "خايس", "بشع", "مقرف", "مشكله", "غش",
]);
const NEGATIVE_PHRASES = ["rip off", "don't trust", "do not trust", "not working", "doesn't work", "does not work", "waste of", "هذا نصب", "ما بيشتغل", "لا يعمل", "ne marche pas", "no funciona"];
/** Interrogatives: a question when one of them opens the comment. */
const INTERROGATIVES = new Set([
  "how", "what", "where", "when", "which", "who", "why", "much", "can", "could", "do", "does", "is", "are", "any",
  "comment", "combien", "ou", "où", "quand", "quel", "quelle", "pourquoi", "peut", "est",
  "como", "cómo", "cuanto", "cuánto", "donde", "dónde", "cuando", "cuándo", "cual", "cuál", "puedo", "hay",
  "كيف", "شو", "ايش", "وين", "فين", "بكم", "قديش", "كم", "هل", "ممكن", "متى", "ليش", "لماذا", "اين", "أين",
]);
/** Interest words: a question wherever they appear. */
const INTEREST = new Set([
  "price", "prices", "pricing", "cost", "link", "info", "information", "details", "detail", "available", "availability", "interested", "dm", "download", "contact", "whatsapp",
  "prix", "lien", "infos", "disponible", "intéressé", "interesse", "renseignements",
  "precio", "enlace", "informacion", "información", "interesado",
  "سعر", "اسعار", "أسعار", "معلومات", "تفاصيل", "رابط", "لينك", "مهتم", "مهتمة", "متاح", "تواصل", "واتساب", "بدي", "اريد", "أريد", "رقم",
]);

const URL_RE = /(https?:\/\/|www\.|\b[a-z0-9-]+\.(com|net|org|io|co|me|ly|xyz|shop)\b)/i;
const PROMO_RE = /(follow (me|us|back)|check (my|out my) (page|profile)|promo|giveaway|earn \$|make money|crypto|forex|whatsapp me|buy followers|free followers|subscribe to my)/i;

/** True when the whole comment is emoji / punctuation with at least one emoji. */
function emojiOnly(s: string): boolean {
  const rest = s.replace(EMOJI, "").replace(/[\s.,!?;:'"()\-_*~❤️‍️‍]/gu, "");
  return rest.length === 0 && EMOJI.test(s);
}

export function classifyComment(text: string): CommentKind {
  const raw = (text ?? "").trim();
  if (!raw) return "neutral";
  const t = tokens(raw).filter((w) => !/^@/.test(w));
  const lower = norm(raw);

  if (URL_RE.test(raw) || PROMO_RE.test(raw)) return "spam";
  if (NEGATIVE_EMOJI.test(raw) || t.some((w) => NEGATIVE_WORDS.has(w)) || NEGATIVE_PHRASES.some((p) => lower.includes(p))) return "negative";

  const positiveSignal = POSITIVE_EMOJI.test(raw) || t.some((w) => POSITIVE_WORDS.has(w));
  const asks = /[?؟]/.test(raw) || t.slice(0, 3).some((w) => INTERROGATIVES.has(w)) || t.some((w) => INTEREST.has(w));
  if (asks && !(positiveSignal && !/[?؟]/.test(raw) && !t.some((w) => INTEREST.has(w)))) return "question";

  if (emojiOnly(raw)) return POSITIVE_EMOJI.test(raw) ? "positive" : "neutral";
  if (t.length <= 8 && (POSITIVE_EMOJI.test(raw) || t.some((w) => POSITIVE_WORDS.has(w)))) return "positive";
  return "neutral";
}

const FR = new Set(["merci", "bravo", "super", "salut", "bonjour", "comment", "prix", "lien", "pour", "vous", "est", "magnifique", "génial", "parfait", "beaucoup"]);
const ES = new Set(["gracias", "hola", "como", "cómo", "precio", "enlace", "para", "excelente", "genial", "felicidades", "cuanto", "cuánto", "muchas"]);

export function commentLang(text: string): CommentLang {
  if (/[؀-ۿ]/.test(text)) return "ar";
  const t = tokens(text);
  if (t.some((w) => FR.has(w))) return "fr";
  if (t.some((w) => ES.has(w))) return "es";
  return "en";
}
