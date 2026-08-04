import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { AdminComponent } from "./admin.component";
import { AdminService } from "../../services/admin.service";
import { AdminUserSummary } from "../../models/bank.models";

const ALICE: AdminUserSummary = {
  id: "1",
  username: "alice",
  email: "alice@example.com",
  active: true,
  kycStatus: "VERIFIED",
};

const BOB: AdminUserSummary = {
  id: "2",
  username: "bob",
  email: "bob@example.com",
  active: true,
  kycStatus: "PENDING",
};

describe("AdminComponent", () => {
  let component: AdminComponent;
  let fixture: ComponentFixture<AdminComponent>;
  let adminService: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    fixture = TestBed.createComponent(AdminComponent);
    component = fixture.componentInstance;
    adminService = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function loadUsers(users: AdminUserSummary[] = [ALICE, BOB]): void {
    component.ngOnInit();
    httpMock.expectOne("/api/admin/users").flush(users);
  }

  it("loads the user list on init", () => {
    loadUsers();

    expect(component.users()).toEqual([ALICE, BOB]);
    expect(component.loading()).toBe(false);
  });

  it("retry() re-triggers the load after a failure", () => {
    component.ngOnInit();
    httpMock.expectOne("/api/admin/users").flush("boom", { status: 500, statusText: "Server Error" });
    expect(component.error()).toBe("Failed to load users.");

    component.retry();
    httpMock.expectOne("/api/admin/users").flush([ALICE]);
    expect(component.error()).toBeNull();
  });

  describe("toggle confirmation", () => {
    it("does not change anything until confirmed", () => {
      loadUsers();
      const user = component.users()[0];

      component.requestToggle(user);

      expect(component.pendingToggle()).toEqual(user);
      expect(adminService.getUser(user.id)?.active).toBe(user.active);
    });

    it("cancelToggle() clears the pending confirmation without calling the service", () => {
      loadUsers();
      const user = component.users()[0];
      component.requestToggle(user);

      component.cancelToggle();

      expect(component.pendingToggle()).toBeNull();
      expect(adminService.getUser(user.id)?.active).toBe(user.active);
    });

    it("confirmToggle() flips the user's active flag and clears the pending confirmation", () => {
      loadUsers();
      const user = component.users()[0];
      component.requestToggle(user);

      component.confirmToggle();
      httpMock.expectOne(`/api/admin/users/${user.id}/active`).flush({ ...user, active: !user.active });

      expect(component.pendingToggle()).toBeNull();
      expect(adminService.getUser(user.id)?.active).toBe(!user.active);
    });

    it("confirmToggle() is a no-op when nothing is pending", () => {
      loadUsers();
      expect(() => component.confirmToggle()).not.toThrow();
    });
  });

  describe("send message", () => {
    it("openSendMessage() resets the form fields for the new target", () => {
      loadUsers();
      const user = component.users()[1];
      component.messageSubject = "stale";
      component.messageBody = "stale";
      component.messagePriority = "HIGH";

      component.openSendMessage(user);

      expect(component.messageTarget()).toEqual(user);
      expect(component.messageSubject).toBe("");
      expect(component.messageBody).toBe("");
      expect(component.messagePriority).toBe("NORMAL");
    });

    it("closeSendMessage() clears the target", () => {
      loadUsers();
      component.openSendMessage(component.users()[0]);

      component.closeSendMessage();

      expect(component.messageTarget()).toBeNull();
    });

    it("sendMessage() does nothing when the form is invalid", () => {
      loadUsers();
      const user = component.users()[0];
      component.openSendMessage(user);

      component.sendMessage({ invalid: true } as any, user);

      httpMock.expectNone("/api/admin/messages");
      expect(component.messageTarget()).toEqual(user);
    });

    it("sendMessage() posts the form values and closes the modal on success", () => {
      loadUsers();
      const user = component.users()[0];
      component.openSendMessage(user);
      component.messageSubject = "Heads up";
      component.messageBody = "Please verify your documents.";
      component.messagePriority = "HIGH";

      component.sendMessage({ invalid: false } as any, user);

      const req = httpMock.expectOne("/api/admin/messages");
      expect(req.request.body).toEqual({
        ownerId: user.id,
        subject: "Heads up",
        body: "Please verify your documents.",
        priority: "HIGH",
      });
      req.flush(null);

      expect(component.messageTarget()).toBeNull();
      expect(component.sendingMessage()).toBe(false);
    });

    it("sendMessage() shows an error and keeps the modal open on failure", () => {
      loadUsers();
      const user = component.users()[0];
      component.openSendMessage(user);
      component.messageSubject = "Heads up";
      component.messageBody = "Please verify your documents.";

      component.sendMessage({ invalid: false } as any, user);
      httpMock.expectOne("/api/admin/messages").flush("boom", { status: 500, statusText: "Server Error" });

      expect(component.messageTarget()).toEqual(user);
      expect(component.messageError()).toBe("Failed to send message. Please try again.");
      expect(component.sendingMessage()).toBe(false);
    });
  });

});
