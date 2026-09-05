import { initializeApp, getApps } from "firebase-admin/app";

// Shared Admin SDK app instance. Cloud Functions provides application-default
// credentials automatically at runtime — no key file needed here.
export const adminApp = getApps().length === 0 ? initializeApp() : getApps()[0];
