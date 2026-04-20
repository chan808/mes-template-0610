import RoomSpaceView from "@/features/room/components/RoomSpaceView";

interface RoomPageProps {
  params: Promise<{ id: string }>;
}

export default async function RoomPage({ params }: RoomPageProps) {
  const { id } = await params;
  return <RoomSpaceView roomId={Number(id)} />;
}
