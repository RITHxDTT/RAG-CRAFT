import { PublicChat } from "@/features/public-chat/public-chat";
export default async function Share({params}:{params:Promise<{token:string;shareToken:string}>}) { const {token,shareToken}=await params;return <PublicChat kind="share" token={shareToken} botSlug={token}/>; }
