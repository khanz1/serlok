CREATE TABLE "devices" (
	"user_id" uuid NOT NULL,
	"device_instance_id" text NOT NULL,
	"manufacturer" text NOT NULL,
	"model" text NOT NULL,
	"android_version" text NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "devices_user_id_device_instance_id_pk" PRIMARY KEY("user_id","device_instance_id")
);

CREATE TABLE "room_members" (
	"room_code" char(6) NOT NULL,
	"user_id" uuid NOT NULL,
	"joined_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "room_members_room_code_user_id_pk" PRIMARY KEY("room_code","user_id")
);

CREATE TABLE "rooms" (
	"code" char(6) PRIMARY KEY NOT NULL,
	"master_id" uuid NOT NULL,
	"is_sharing" boolean DEFAULT false NOT NULL,
	"shake_threshold" real DEFAULT 3.5 NOT NULL,
	"location_interval_ms" integer DEFAULT 10000 NOT NULL,
	"closed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "rooms_shake_threshold_check" CHECK ("rooms"."shake_threshold" IN (2.5, 3.5, 4.5)),
	CONSTRAINT "rooms_location_interval_ms_check" CHECK ("rooms"."location_interval_ms" IN (2000, 5000, 10000, 30000, 60000))
);

CREATE TABLE "sos_events" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"room_code" char(6) NOT NULL,
	"user_id" uuid NOT NULL,
	"idempotency_key" uuid DEFAULT gen_random_uuid() NOT NULL,
	"magnitude" real NOT NULL,
	"x" real NOT NULL,
	"y" real NOT NULL,
	"z" real NOT NULL,
	"latitude" real,
	"longitude" real,
	"accuracy" real,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "sos_events_coordinates_check" CHECK (("sos_events"."latitude" IS NULL OR "sos_events"."latitude" BETWEEN -90 AND 90)
          AND ("sos_events"."longitude" IS NULL OR "sos_events"."longitude" BETWEEN -180 AND 180)),
	CONSTRAINT "sos_events_magnitude_check" CHECK ("sos_events"."magnitude" >= 0)
);

CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"name" text NOT NULL,
	"email" text NOT NULL,
	"password_hash" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "users_email_unique" UNIQUE("email")
);

ALTER TABLE "devices" ADD CONSTRAINT "devices_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "room_members" ADD CONSTRAINT "room_members_room_code_rooms_code_fk" FOREIGN KEY ("room_code") REFERENCES "public"."rooms"("code") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "room_members" ADD CONSTRAINT "room_members_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "rooms" ADD CONSTRAINT "rooms_master_id_users_id_fk" FOREIGN KEY ("master_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "sos_events" ADD CONSTRAINT "sos_events_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
CREATE UNIQUE INDEX "sos_events_owner_idempotency_idx" ON "sos_events" USING btree ("user_id","idempotency_key");
