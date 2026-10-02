import {
  onCall as v2OnCall,
  CallableFunction,
  CallableOptions,
  CallableRequest,
  CallableResponse,
} from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

/**
 * Single switch for App Check on every callable.
 * Enforcing blocks unauthorized clients (e.g., cURL, scripts, modified APKs)
 * from invoking your Cloud Functions.
 */
export const ENFORCE_APP_CHECK = true;

type Handler<T, Return, Stream> = (request: CallableRequest<T>, response?: CallableResponse<Stream>) => Return;
type Result<Return> = Return extends Promise<unknown> ? Return : Promise<Return>;

function withAppCheckAudit<T, Return, Stream>(handler: Handler<T, Return, Stream>): Handler<T, Return, Stream> {
  return (request, response) => {
    if (!request.app) {
      logger.warn("app_check_unverified", {
        fn: process.env.FUNCTION_TARGET ?? process.env.K_SERVICE ?? "unknown",
        uid: request.auth?.uid ?? null,
      });
    }
    return handler(request, response);
  };
}

/** Drop-in replacement for firebase-functions/v2/https `onCall` that applies the App Check policy. */
export function onCall<T = any, Return = any | Promise<any>, Stream = unknown>(
  opts: CallableOptions<T>,
  handler: Handler<T, Return, Stream>
): CallableFunction<T, Result<Return>, Stream>;
export function onCall<T = any, Return = any | Promise<any>, Stream = unknown>(
  handler: Handler<T, Return, Stream>
): CallableFunction<T, Result<Return>, Stream>;
export function onCall<T = any, Return = any | Promise<any>, Stream = unknown>(
  optsOrHandler: CallableOptions<T> | Handler<T, Return, Stream>,
  maybeHandler?: Handler<T, Return, Stream>
): CallableFunction<T, Result<Return>, Stream> {
  const opts: CallableOptions<T> = typeof optsOrHandler === "function" ? {} : optsOrHandler;
  const handler = (typeof optsOrHandler === "function" ? optsOrHandler : maybeHandler) as Handler<T, Return, Stream>;
  return v2OnCall<T, Return, Stream>(
    { enforceAppCheck: ENFORCE_APP_CHECK, ...opts },
    withAppCheckAudit(handler)
  );
}
