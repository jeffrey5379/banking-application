import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { AdminService } from "./admin.service";
import { AdminUserSummary } from "../models/bank.models";

const BOB: AdminUserSummary = {
  id: "2",
  username: "bob",
  email: "bob@example.com",
  active: true,
  kycStatus: "PENDING",
};

const CAROL: AdminUserSummary = {
  id: "3",
  username: "carol",
  email: "carol@example.com",
  active: true,
  kycStatus: "NOT_STARTED",
};

describe("AdminService", () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe("loadIfNeeded()", () => {
    it("GET /api/admin/users, populates users()", () => {
      service.loadIfNeeded();

      const req = httpMock.expectOne("/api/admin/users");
      expect(req.request.method).toBe("GET");
      req.flush([BOB, CAROL]);

      expect(service.users()).toEqual([BOB, CAROL]);
      expect(service.loading()).toBe(false);
      expect(service.error()).toBeNull();
    });

    it("does not re-fetch once already loaded", () => {
      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users").flush([BOB]);

      service.loadIfNeeded();
      httpMock.expectNone("/api/admin/users");
    });

    it("does not fire a second request while the first is still in flight", () => {
      service.loadIfNeeded();
      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users");
    });

    it("sets error() on failure and allows a retry", () => {
      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users").flush("boom", { status: 500, statusText: "Server Error" });

      expect(service.error()).toBe("Failed to load users.");
      expect(service.loading()).toBe(false);

      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users").flush([BOB]);
      expect(service.error()).toBeNull();
    });
  });

  describe("getUser()", () => {
    it("returns the matching loaded user", () => {
      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users").flush([BOB, CAROL]);

      expect(service.getUser("3")).toEqual(CAROL);
    });

    it("returns undefined for an unknown id", () => {
      expect(service.getUser("does-not-exist")).toBeUndefined();
    });
  });

  describe("setActive()", () => {
    it("optimistically flips the user's active flag, then POSTs the change", () => {
      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users").flush([BOB]);

      service.setActive("2", false);
      expect(service.getUser("2")?.active).toBe(false);

      const req = httpMock.expectOne("/api/admin/users/2/active");
      expect(req.request.method).toBe("POST");
      expect(req.request.body).toEqual({ active: false });
      req.flush({ ...BOB, active: false });
    });

    it("rolls back on failure", () => {
      service.loadIfNeeded();
      httpMock.expectOne("/api/admin/users").flush([BOB]);

      service.setActive("2", false);
      expect(service.getUser("2")?.active).toBe(false);

      httpMock
        .expectOne("/api/admin/users/2/active")
        .flush("boom", { status: 500, statusText: "Server Error" });

      expect(service.getUser("2")?.active).toBe(true);
    });
  });

  describe("sendMessage()", () => {
    it("POSTs to /api/admin/messages with ownerId, subject, body, and priority", () => {
      service.sendMessage("2", "Heads up", "Please verify your documents.", "HIGH").subscribe();

      const req = httpMock.expectOne("/api/admin/messages");
      expect(req.request.method).toBe("POST");
      expect(req.request.body).toEqual({
        ownerId: "2",
        subject: "Heads up",
        body: "Please verify your documents.",
        priority: "HIGH",
      });
      req.flush(null);
    });
  });
});
