/**
 * Mobile money behind one interface. Milestone 2 adds MTN MoMo (sandbox first), then Airtel Money.
 * Money is held by Liso ("Liso Protect") until the buyer confirms receipt, or 48 h pass with no complaint.
 */
export interface WalletProvider {
  readonly network: "mtn" | "airtel";
  /** Ask the buyer's phone to approve a payment into the Liso wallet. */
  collect(req: { dealId: string; phone: string; amountUgx: bigint }): Promise<{ ref: string }>;
  /** Pay the seller from the Liso wallet, net of Liso's fee. */
  disburse(req: { dealId: string; phone: string; amountUgx: bigint }): Promise<{ ref: string }>;
  status(ref: string): Promise<"pending" | "successful" | "failed">;
}
