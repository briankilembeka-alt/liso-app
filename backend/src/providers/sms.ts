export interface SmsProvider {
  send(phone: string, text: string): Promise<void>;
}

/** Development only: writes the message to the log instead of sending it. */
export class ConsoleSms implements SmsProvider {
  readonly sent: { phone: string; text: string }[] = [];
  async send(phone: string, text: string) {
    this.sent.push({ phone, text });
    console.log(`[sms] to ${phone}: ${text}`);
  }
}
