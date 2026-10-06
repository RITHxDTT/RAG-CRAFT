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
}
