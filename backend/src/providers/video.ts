import {
  IVSRealTimeClient,
  CreateStageCommand,
  CreateParticipantTokenCommand,
  DeleteStageCommand,
} from "@aws-sdk/client-ivs-realtime";

export interface JoinToken {
  token: string;
  expiresAt: string;
  /** Max video the client should publish. 360p by default for entry-level phones and data cost. */
  publish: { width: number; height: number; maxBitrateKbps: number };
}

/**
 * The rented video layer. 1:1 calls are WebRTC rooms ("stages" in Amazon IVS).
 * Everything provider-specific stays behind this interface so we can move to LiveKit later.
 */
export interface VideoProvider {
  createRoom(callId: string): Promise<{ roomRef: string }>;
  joinToken(roomRef: string, userId: string, role: "buyer" | "seller"): Promise<JoinToken>;
  closeRoom(roomRef: string): Promise<void>;
}

export const DEFAULT_PUBLISH = { width: 640, height: 360, maxBitrateKbps: 600 };
const TOKEN_MINUTES = 60;

export class FakeVideo implements VideoProvider {
  readonly closed: string[] = [];
  async createRoom(callId: string) {
    return { roomRef: `fake-room-${callId}` };
  }
  async joinToken(roomRef: string, userId: string, role: "buyer" | "seller") {
    return {
      token: `fake-token:${roomRef}:${role}:${userId}`,
      expiresAt: new Date(Date.now() + TOKEN_MINUTES * 60_000).toISOString(),
      publish: DEFAULT_PUBLISH,
    };
  }
  async closeRoom(roomRef: string) {
    this.closed.push(roomRef);
  }
}

/**
 * Amazon IVS Real-Time. Needs AWS credentials in the environment.
 * TODO(milestone 1): attach a storage configuration so calls are recorded at ~360p to S3,
 * with the on-screen "this call is recorded" notice on both phones.
 */
export class IvsVideo implements VideoProvider {
  private readonly client: IVSRealTimeClient;
  constructor(region: string) {
    this.client = new IVSRealTimeClient({ region });
  }
  async createRoom(callId: string) {
    const out = await this.client.send(
      new CreateStageCommand({ name: `call-${callId}`, tags: { app: "liso", callId } }),
    );
    const arn = out.stage?.arn;
    if (!arn) throw new Error("IVS did not return a stage ARN");
    return { roomRef: arn };
  }
  async joinToken(roomRef: string, userId: string, role: "buyer" | "seller") {
    const out = await this.client.send(
      new CreateParticipantTokenCommand({
        stageArn: roomRef,
        userId,
        duration: TOKEN_MINUTES,
        capabilities: ["PUBLISH", "SUBSCRIBE"],
        attributes: { role },
      }),
    );
    const t = out.participantToken;
    if (!t?.token) throw new Error("IVS did not return a participant token");
    return {
      token: t.token,
      expiresAt: (t.expirationTime ?? new Date(Date.now() + TOKEN_MINUTES * 60_000)).toISOString(),
      publish: DEFAULT_PUBLISH,
    };
  }
  async closeRoom(roomRef: string) {
    await this.client.send(new DeleteStageCommand({ arn: roomRef }));
  }
}
