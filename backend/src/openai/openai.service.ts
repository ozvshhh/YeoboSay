import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import OpenAI, { toFile } from 'openai';

export interface AssistantMessage {
  role: 'user' | 'assistant';
  content: string;
}

const DEFAULT_STT_MODEL = 'gpt-4o-mini-transcribe';
const DEFAULT_RESPONSE_MODEL = 'gpt-5.2';
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

  async generateAssistantText(
    messages: AssistantMessage[],
    extraInstructions?: string,
  ): Promise<string> {
    const response = await this.getClient().responses.create({
      model: this.responseModel,
      instructions: [
        'You are YeoboSay, a warm voice companion for elderly users.',
        'Keep replies natural, kind, and concise.',
        'Reply in Korean when the user speaks Korean, and in English when the user speaks English.',
        'Use 1 to 3 short sentences.',
        'If the user describes immediate danger, self-harm, or a medical emergency, gently tell them to contact 119 or a nearby person right away.',
        extraInstructions,
      ].join(' '),
      input: messages.map((message) => ({
        role: message.role,
        content: message.content,
      })),
      max_output_tokens: 220,
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
