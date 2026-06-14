import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import OpenAI, { toFile } from 'openai';

export interface AssistantMessage {
  role: 'user' | 'assistant';
  content: string;
}

const DEFAULT_STT_MODEL = 'gpt-4o-mini-transcribe';
const DEFAULT_RESPONSE_MODEL = 'gpt-4o-mini';
const DEFAULT_TTS_MODEL = 'gpt-4o-mini-tts';
const DEFAULT_TTS_VOICE = 'shimmer';

@Injectable()
export class OpenAiService {
  private readonly client: OpenAI | null;
  private readonly sttModel: string;
  private readonly responseModel: string;
  private readonly ttsModel: string;
  private readonly ttsVoice: string;

  constructor(private readonly configService: ConfigService) {
    const apiKey = this.configService.get<string>('OPENAI_API_KEY');

    this.client = apiKey ? new OpenAI({ apiKey }) : null;
    this.sttModel =
      this.configService.get<string>('OPENAI_STT_MODEL') ?? DEFAULT_STT_MODEL;
    this.responseModel =
      this.configService.get<string>('OPENAI_RESPONSE_MODEL') ??
      DEFAULT_RESPONSE_MODEL;
    this.ttsModel =
      this.configService.get<string>('OPENAI_TTS_MODEL') ?? DEFAULT_TTS_MODEL;
    this.ttsVoice =
      this.configService.get<string>('OPENAI_TTS_VOICE') ?? DEFAULT_TTS_VOICE;
  }

  async transcribeAudio(
    buffer: Buffer,
    filename: string,
    mimeType: string,
  ): Promise<string> {
    const file = await toFile(buffer, filename, { type: mimeType });
    const transcription = await this.getClient().audio.transcriptions.create({
      file,
      model: this.sttModel,
    });

    return transcription.text.trim();
  }

  async generateAssistantText(messages: AssistantMessage[]): Promise<string> {
    const response = await this.getClient().responses.create({
      model: this.responseModel,
      instructions: [
        'You are YeoboSay, a warm voice companion for elderly users.',
        'For the current demo, guide the call through a stable morning check-in flow.',
        'The first greeting is handled separately, so continue from the user answer.',
        'Flow: 1) respond warmly to the user mood/sleep answer, then ask whether they had breakfast and took their blood pressure medicine.',
        '2) After the user answers about breakfast or medication, respond naturally and ask whether they measured blood pressure today and what the numbers were.',
        '3) After the user answers blood pressure, respond reassuringly, check overall health briefly, and begin closing the call.',
        '4) The final closing must include exactly this reminder in Korean: "다음주 목요일 병원 예약해둔 것 잊지 마세요!"',
        'Ask only one compact question per response unless the flow specifically asks breakfast and medication together.',
        'Do not restart the greeting once the conversation has begun.',
        'Keep replies natural, kind, and concise.',
        'Reply in Korean when the user speaks Korean, and in English when the user speaks English.',
        'Use 1 to 2 short sentences.',
        'If the user describes immediate danger, self-harm, or a medical emergency, gently tell them to contact 119 or a nearby person right away.',
      ].join(' '),
      input: messages.map((message) => ({
        role: message.role,
        content: message.content,
      })),
      max_output_tokens: 100,
    });

    const text = response.output_text.trim();

    if (!text) {
      throw new Error('OpenAI returned an empty assistant response.');
    }

    return text;
  }

  async synthesizeSpeech(text: string): Promise<Buffer> {
    const speech = await this.getClient().audio.speech.create({
      model: this.ttsModel,
      voice: this.ttsVoice,
      input: text,
      response_format: 'mp3',
    });

    return Buffer.from(await speech.arrayBuffer());
  }

  private getClient(): OpenAI {
    if (!this.client) {
      throw new ServiceUnavailableException(
        'OPENAI_API_KEY must be set before using OpenAI voice features.',
      );
    }

    return this.client;
  }
}
