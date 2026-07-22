import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 2,
  duration: "20s",
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const password = __ENV.BENCH_PASSWORD || "correct-horse-battery";

export default function () {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const email = `bench+${suffix}@example.com`;

  const register = http.post(
    `${baseUrl}/api/auth/register`,
    JSON.stringify({
      email,
      displayName: "Benchmark User",
      password,
    }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(register, { "register created": (res) => res.status === 201 });

  const token = register.json("token");
  const authHeaders = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };

  const listBefore = http.get(`${baseUrl}/api/notes?page=0&size=20&sort=updatedAt,desc`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(listBefore, { "list before ok": (res) => res.status === 200 });

  const create = http.post(
    `${baseUrl}/api/notes`,
    JSON.stringify({ title: "k6 note", content: "Local benchmark placeholder content" }),
    { headers: authHeaders },
  );
  check(create, { "note created": (res) => res.status === 201 });

  sleep(1);
}
