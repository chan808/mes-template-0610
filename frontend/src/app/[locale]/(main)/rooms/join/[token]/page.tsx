import JoinRoomView from "@/features/room/components/JoinRoomView";

interface JoinRoomPageProps {
  params: Promise<{ token: string }>;
}

export default async function JoinRoomPage({ params }: JoinRoomPageProps) {
  const { token } = await params;
  return <JoinRoomView token={token} />;
}
