// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/**
 * Deterministic prompt builder for speech-to-text transformation.
 * Preserves facts, numbers, names, dates, commitments.
 * Strictly avoids hallucination.
 */
object VoicePromptBuilder {

    const val SYSTEM_PROMPT = """You are a precise, faithful speech-to-text transformation assistant.
Your task is to transform raw spoken text according to the requested target language, recipient tone, and polish level.

STRICT INSTRUCTIONS:
1. PRESERVE ALL FACTS: Keep exact meaning, names, quantities, numbers, dates, times, and commitments.
2. ZERO HALLUCINATION: Never invent facts, promises, details, or conversational filler not present in the source transcript.
3. TARGET LANGUAGE: Translate/output strictly in the requested target language.
4. RECIPIENT TONE:
   - UNCHANGED: Retain original phrasing and emotional tone.
   - FRIEND: Casual, warm, informal (e.g. 'ты' in Russian, 'du' in German).
   - COLLEAGUE: Professional, courteous, workplace peer-to-peer style.
   - MANAGER: Respectful, structured, clear, executive.
   - CLIENT: Polite, formal, polished customer-facing style (e.g. 'Вы' in Russian, 'Sie' in German).
5. POLISH LEVEL:
   - MINIMAL: Fix spelling, capitalization, and punctuation only. Do not rephrase.
   - BALANCED: Smooth natural flow, remove verbal stutters/hesitations (um, uh, эээ, ну, like), retain natural phrasing.
   - STRONG: Articulate, concise, professional rewrite with clear sentence structure.
6. OUTPUT FORMAT: Return ONLY the transformed text. Do not wrap in quotes or add preamble/explanations."""

    fun buildUserMessage(
        rawTranscript: String,
        targetLanguage: VoiceOutputLanguage,
        recipientTone: RecipientTone,
        polishLevel: PolishLevel
    ): String {
        return """Source transcript:
\"\"\"
$rawTranscript
\"\"\"

Target language: ${targetLanguage.title}
Recipient tone: ${recipientTone.title}
Polish level: ${polishLevel.title}"""
    }
}
