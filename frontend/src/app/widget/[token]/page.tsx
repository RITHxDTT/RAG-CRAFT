import { PublicChat } from "@/features/public-chat/public-chat";
export default async function Widget({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;
  return <PublicChat kind="widget" token={token} />;
}
