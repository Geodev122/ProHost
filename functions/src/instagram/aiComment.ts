import { CommentLang } from "./commentClass";

/** Pure texts and plans for the comment agent. */

const PUBLIC_THANKS: Record<CommentLang, string[]> = {
  en: ["Thank you @{u}! 🙏", "Thanks so much @{u}! ❤️", "Really appreciate it @{u}! 🙌", "Thank you for the love @{u}! ✨"],
  ar: ["شكراً لك @{u}! 🙏", "ألف شكر @{u}! ❤️", "نقدّر لطفك @{u}! 🙌", "يسلمو @{u}! ✨"],
  fr: ["Merci beaucoup @{u} ! 🙏", "Merci @{u}, ça fait plaisir ! ❤️", "Super gentil @{u} ! 🙌"],
  es: ["¡Muchas gracias @{u}! 🙏", "¡Gracias @{u}, se agradece! ❤️", "¡Qué lindo @{u}! 🙌"],
};
const PUBLIC_SEE_DM: Record<CommentLang, string[]> = {
  en: ["Thanks for your interest @{u}! We just sent you the answer in your DMs 📩", "Great question @{u}! Check your DMs, the answer is there 📩"],
  ar: ["شكراً لاهتمامك @{u}! أرسلنا لك الجواب في الرسائل الخاصة 📩", "سؤال ممتاز @{u}! تفقّد رسائلك الخاصة، الجواب هناك 📩"],
  fr: ["Merci pour votre intérêt @{u} ! La réponse vous attend en message privé 📩", "Bonne question @{u} ! Regardez vos messages privés 📩"],
  es: ["¡Gracias por tu interés @{u}! Te enviamos la respuesta por mensaje privado 📩", "¡Buena pregunta @{u}! Revisa tus mensajes privados 📩"],
};
const DM_HOOK: Record<CommentLang, string> = {
  en: "Hi @{u}, I'm ProHost's automated assistant, thank you for the love on our post 🙌 Out of curiosity, what kind of work do you do, and are you looking for a space to rent or one to list? Reply STOP and I won't message you again.",
  ar: "أهلاً @{u}، أنا المساعد الآلي لـ ProHost، شكراً على لطفك في تعليقك 🙌 من باب الفضول، ما هو مجال عملك، وهل تبحث عن مساحة لتستأجرها أم لديك مساحة تريد عرضها؟ أرسل STOP ولن أراسلك مجدداً.",
  fr: "Bonjour @{u}, je suis l'assistant automatique de ProHost, merci pour votre gentil commentaire 🙌 Par curiosité, quel est votre métier, et cherchez-vous un espace à louer ou un espace à proposer ? Répondez STOP et je ne vous écrirai plus.",
  es: "Hola @{u}, soy el asistente automático de ProHost, gracias por tu comentario 🙌 Por curiosidad, ¿a qué te dedicas y buscas un espacio para alquilar o uno para publicar? Responde STOP y no te escribiré más.",
};

const pick = <T>(list: T[], seed: string): T => {
  let h = 0;
  for (const c of seed) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return list[h % list.length];
};
const fill = (t: string, user?: string) => t.replace(/@\{u\}/g, user ? `@${user}` : "").replace(/\s+([!.,؟?])/g, "$1").replace(/\s{2,}/g, " ").trim();

export const publicThanks = (lang: CommentLang, user: string | undefined, seed: string) => fill(pick(PUBLIC_THANKS[lang], seed), user);
export const publicSeeDm = (lang: CommentLang, user: string | undefined, seed: string) => fill(pick(PUBLIC_SEE_DM[lang], seed), user);
export const dmHook = (lang: CommentLang, user: string | undefined) => fill(DM_HOOK[lang], user);

/** Extra context for the model when the DM is the answer to a public comment (private replies allow ONE message). */
export function commentModeContext(commentText: string): string {
  return `COMMENT MODE (from the server): the person wrote this PUBLIC comment under our post: "${commentText.slice(0, 300).replace(/"/g, "'")}". They have NOT written to us in DM yet. Write ONE single DM message (a single string in "messages", no greeting-then-question split, under 600 characters) that answers their question directly, warmly, in their language. Do not ask for their email or name now. Set awaitReply true only if you end with a question. Put the most useful button first.`;
}
