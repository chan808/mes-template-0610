"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from "@/shared/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/shared/components/ui/form";
import { Input } from "@/shared/components/ui/input";
import { Button } from "@/shared/components/ui/button";
import React, { useState, type ReactNode } from "react";
import { useCreateRoom } from "../hooks/useRooms";

const schema = z.object({
  name: z.string().min(1, "방 이름을 입력해주세요.").max(50, "50자 이내로 입력해주세요."),
  maxCapacity: z.number().min(1, "최소 1명 이상이어야 합니다.").max(100, "최대 100명까지 설정 가능합니다."),
  isPrivate: z.boolean(),
});

type FormValues = z.infer<typeof schema>;

interface CreateRoomDialogProps {
  children: ReactNode;
}

export default function CreateRoomDialog({ children }: CreateRoomDialogProps) {
  const [open, setOpen] = useState(false);
  const { mutate: createRoom, isPending } = useCreateRoom();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      maxCapacity: 10,
      isPrivate: false,
    },
  });

  function onSubmit(values: FormValues) {
    createRoom(values, {
      onSuccess: () => {
        form.reset();
        setOpen(false);
      },
    });
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>새 방 만들기</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>방 이름</FormLabel>
                  <FormControl>
                    <Input placeholder="방 이름을 입력하세요" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="maxCapacity"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>최대 인원</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min={1}
                      max={100}
                      {...field}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => field.onChange(e.target.valueAsNumber)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="isPrivate"
              render={({ field }) => (
                <FormItem>
                  <div className="flex items-center gap-2">
                    <input
                      id="isPrivate"
                      type="checkbox"
                      checked={field.value}
                      onChange={field.onChange}
                      className="h-4 w-4 rounded border-input accent-primary"
                    />
                    <FormLabel htmlFor="isPrivate" className="cursor-pointer">
                      비공개 방
                    </FormLabel>
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button type="submit" disabled={isPending}>
                {isPending ? "생성 중..." : "만들기"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
