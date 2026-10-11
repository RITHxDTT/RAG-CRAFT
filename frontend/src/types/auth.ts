export interface CurrentUser {
  id: string;
  email: string;
  organization_id: string;
  organization_name: string;
  role: "ADMIN" | "USER";
  full_name: string;
  display_name?: string;
  bio?: string;
  avatar?: string;
  theme?: "light" | "dark";
  language?: "en" | "ko" | "km";
  timezone?: string;
  /** E-mail waiting for verification after a change request. */
  pending_email?: string | null;
  status?: AccountStatus;
  signup_method?: SignupMethod;
  totp_enabled?: boolean;
  /** Admins are required to use an authenticator app. */
  mfa_required?: boolean;
  passkey_count?: number;
  linked_accounts?: SocialProvider[];
  deletion_requested_at?: string | null;
}
export type AccountStatus = "UNVERIFIED" | "ACTIVE" | "SUSPENDED" | "PENDING_DELETION";
export type SignupMethod = "EMAIL" | "GOOGLE" | "GITHUB";
export type SocialProvider = "GOOGLE" | "GITHUB";
