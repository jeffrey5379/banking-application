import {
  Component,
  ChangeDetectionStrategy,
  ElementRef,
  OnDestroy,
  ViewChild,
  input,
  output,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";

// A captured photo isn't emitted immediately - it's held back and shown as a large preview so the
// user can judge sharpness/glare/framing before committing, since a blurry KYC photo means an
// admin rejection days later. photoCaptured only fires once the user confirms it.
@Component({
  selector: "app-camera-capture",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="camera-capture">
      @if (!streamActive() && !capturedImage()) {
        <button
          class="btn btn-ghost btn-sm"
          type="button"
          [disabled]="disabled() || opening()"
          (click)="openCamera()"
        >
          {{ opening() ? "Requesting camera…" : "Use Camera" }}
        </button>
      }

      <video
        #videoElement
        class="camera-video"
        [class.camera-video-hidden]="!streamActive()"
        autoplay
        playsinline
        muted
      ></video>

      @if (streamActive()) {
        <div class="camera-actions">
          <button class="btn btn-primary btn-sm" type="button" (click)="capture()">
            Take Photo
          </button>
          <button class="btn btn-ghost btn-sm" type="button" (click)="closeCamera()">
            Cancel
          </button>
        </div>
      }

      @if (capturedImage()) {
        <img class="captured-preview" [src]="capturedImage()" alt="Captured photo preview" />
        <div class="camera-actions">
          <button class="btn btn-primary btn-sm" type="button" (click)="confirm()">
            Use this photo
          </button>
          <button class="btn btn-ghost btn-sm" type="button" (click)="retake()">
            Retake
          </button>
        </div>
      }

      @if (error()) {
        <span class="field-hint field-hint-error">{{ error() }}</span>
      }

      <canvas #canvasElement class="camera-canvas-hidden"></canvas>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .camera-capture {
        display: flex;
        flex-direction: column;
        gap: 8px;
        align-items: flex-start;
      }
      .camera-video {
        width: 100%;
        max-width: 480px;
        aspect-ratio: 4 / 3;
        object-fit: cover;
        border-radius: var(--radius-sm);
        background: #000;
      }
      .camera-video-hidden {
        display: none;
      }
      .captured-preview {
        width: 100%;
        max-width: 480px;
        max-height: 480px;
        object-fit: contain;
        border-radius: var(--radius-sm);
        border: 1px solid var(--border-subtle);
        background: var(--surface-inset);
      }
      .camera-actions {
        display: flex;
        gap: 8px;
      }
      .camera-canvas-hidden {
        display: none;
      }
      .field-hint-error {
        color: var(--danger);
        font-size: 12px;
      }
    `,
  ],
})
export class CameraCaptureComponent implements OnDestroy {
  facingMode = input<"user" | "environment">("environment");
  fileName = input("photo.jpg");
  disabled = input(false);

  photoCaptured = output<File>();

  @ViewChild("videoElement", { static: true })
  private videoElementRef!: ElementRef<HTMLVideoElement>;

  @ViewChild("canvasElement", { static: true })
  private canvasElementRef!: ElementRef<HTMLCanvasElement>;

  streamActive = signal(false);
  opening = signal(false);
  error = signal("");
  capturedImage = signal<string | null>(null);

  private stream: MediaStream | null = null;
  private pendingFile: File | null = null;

  async openCamera(): Promise<void> {
    this.error.set("");
    this.opening.set(true);
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: this.facingMode() } },
        audio: false,
      });
      this.videoElementRef.nativeElement.srcObject = this.stream;
      this.streamActive.set(true);
    } catch (err) {
      this.error.set(
        (err as { name?: string })?.name === "NotAllowedError"
          ? "Camera access was denied. Please allow camera access and try again."
          : "Could not access the camera. Please check your device and try again.",
      );
    } finally {
      this.opening.set(false);
    }
  }

  capture(): void {
    const video = this.videoElementRef.nativeElement;
    const canvas = this.canvasElementRef.nativeElement;
    if (!video.videoWidth || !video.videoHeight) {
      return;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      return;
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (!blob) {
          this.error.set("Could not capture the photo. Please try again.");
          return;
        }
        this.pendingFile = new File([blob], this.fileName(), { type: "image/jpeg" });
        this.capturedImage.set(URL.createObjectURL(blob));
        this.closeCamera();
      },
      "image/jpeg",
      0.92,
    );
  }

  confirm(): void {
    if (!this.pendingFile) {
      return;
    }
    this.photoCaptured.emit(this.pendingFile);
    this.clearCapturedImage();
  }

  retake(): void {
    this.clearCapturedImage();
    this.openCamera();
  }

  closeCamera(): void {
    this.stopStream();
    this.streamActive.set(false);
  }

  ngOnDestroy(): void {
    this.stopStream();
    this.clearCapturedImage();
  }

  private clearCapturedImage(): void {
    const url = this.capturedImage();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.capturedImage.set(null);
    this.pendingFile = null;
  }

  private stopStream(): void {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
  }
}
