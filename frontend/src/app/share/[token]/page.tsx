import { PublicChat } from "@/features/public-chat/public-chat";
export default async function Share({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;
  return <PublicChat kind="share" token={token} />;
}
