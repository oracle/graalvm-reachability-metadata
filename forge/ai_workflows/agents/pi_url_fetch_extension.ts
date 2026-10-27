// Copyright and related rights waived via CC0

/** Bounded URL-fetch tool for Pi artifact discovery. §FS-forge-agent-runtime-selection */

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";

const MAX_RESPONSE_BYTES = 200_000;
const WebFetchParameters = Type.Object({
    url: Type.String({ description: "Absolute HTTP or HTTPS URL to fetch" }),
});

async function readBoundedBody(response: Response): Promise<string> {
    if (response.body === null) {
        return "";
    }
    const reader: ReadableStreamDefaultReader<Uint8Array> = response.body.getReader();
    const decoder = new TextDecoder();
    let byteCount = 0;
    let body = "";
    while (byteCount < MAX_RESPONSE_BYTES) {
        const chunk = await reader.read();
        if (chunk.done) {
            body += decoder.decode();
            return body;
        }
        const remaining = MAX_RESPONSE_BYTES - byteCount;
        const bytes = chunk.value.subarray(0, remaining);
        byteCount += bytes.byteLength;
        body += decoder.decode(bytes, { stream: true });
        if (bytes.byteLength < chunk.value.byteLength) {
            await reader.cancel();
            return `${body}\n\n[Response truncated at ${MAX_RESPONSE_BYTES} bytes]`;
        }
    }
    await reader.cancel();
    return `${body}\n\n[Response truncated at ${MAX_RESPONSE_BYTES} bytes]`;
}

export default function piUrlFetchExtension(pi: ExtensionAPI): void {
    pi.registerTool({
        name: "web_fetch",
        label: "Web fetch",
        description: "Fetch one public HTTP or HTTPS URL for artifact metadata discovery.",
        parameters: WebFetchParameters,
        async execute(_toolCallId, parameters, signal) {
            let url: URL;
            try {
                url = new URL(parameters.url);
            } catch {
                return {
                    content: [{ type: "text", text: `Invalid URL: ${parameters.url}` }],
                    details: { error: true },
                };
            }
            if (url.protocol !== "http:" && url.protocol !== "https:") {
                return {
                    content: [{ type: "text", text: `Unsupported URL protocol: ${url.protocol}` }],
                    details: { error: true },
                };
            }
            try {
                const response: Response = await fetch(url, {
                    redirect: "follow",
                    signal,
                    headers: { "User-Agent": "Forge artifact URL discovery" },
                });
                const body = await readBoundedBody(response);
                const summary = [
                    `URL: ${response.url}`,
                    `Status: ${response.status} ${response.statusText}`,
                    `Content-Type: ${response.headers.get("content-type") || "unknown"}`,
                    "",
                    body,
                ].join("\n");
                return {
                    content: [{ type: "text", text: summary }],
                    details: { status: response.status, url: response.url },
                };
            } catch (error: unknown) {
                const message = error instanceof Error ? error.message : String(error);
                return {
                    content: [{ type: "text", text: `URL fetch failed: ${message}` }],
                    details: { error: true },
                };
            }
        },
    });
}
