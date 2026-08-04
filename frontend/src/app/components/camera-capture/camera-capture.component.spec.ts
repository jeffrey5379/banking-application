import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CameraCaptureComponent } from './camera-capture.component';

describe('CameraCaptureComponent', () => {
  let component: CameraCaptureComponent;
  let fixture: ComponentFixture<CameraCaptureComponent>;
  let getUserMediaMock: jest.Mock;
  let fakeTrack: { stop: jest.Mock };
  let fakeStream: { getTracks: jest.Mock };

  beforeEach(() => {
    fakeTrack = { stop: jest.fn() };
    fakeStream = { getTracks: jest.fn(() => [fakeTrack]) };
    getUserMediaMock = jest.fn().mockResolvedValue(fakeStream);

    Object.defineProperty(globalThis.navigator, 'mediaDevices', {
      value: { getUserMedia: getUserMediaMock },
      configurable: true,
    });

    globalThis.URL.createObjectURL = jest.fn(() => 'blob:mock-preview-url');
    globalThis.URL.revokeObjectURL = jest.fn();

    TestBed.configureTestingModule({
      imports: [CameraCaptureComponent],
    });

    fixture = TestBed.createComponent(CameraCaptureComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('starts with the camera inactive and shows the "Use Camera" trigger', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.streamActive()).toBe(false);
    expect(compiled.textContent).toContain('Use Camera');
  });

  describe('openCamera()', () => {
    it('requests the configured facingMode and activates the stream', async () => {
      fixture.componentRef.setInput('facingMode', 'user');

      await component.openCamera();

      expect(getUserMediaMock).toHaveBeenCalledWith({
        video: { facingMode: { ideal: 'user' } },
        audio: false,
      });
      expect(component.streamActive()).toBe(true);
      expect(component.error()).toBe('');
    });

    it('shows a permission-specific message when access is denied', async () => {
      getUserMediaMock.mockRejectedValueOnce(Object.assign(new Error('denied'), { name: 'NotAllowedError' }));

      await component.openCamera();

      expect(component.error()).toBe(
        'Camera access was denied. Please allow camera access and try again.',
      );
      expect(component.streamActive()).toBe(false);
    });

    it('shows a generic message for any other camera error', async () => {
      getUserMediaMock.mockRejectedValueOnce(new Error('no camera found'));

      await component.openCamera();

      expect(component.error()).toBe(
        'Could not access the camera. Please check your device and try again.',
      );
    });
  });

  function captureAFrame() {
    const video = (component as any).videoElementRef.nativeElement as HTMLVideoElement;
    const canvas = (component as any).canvasElementRef.nativeElement as HTMLCanvasElement;
    Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
    Object.defineProperty(video, 'videoHeight', { value: 480, configurable: true });

    const fakeBlob = new Blob(['fake'], { type: 'image/jpeg' });
    jest.spyOn(canvas, 'getContext').mockReturnValue({ drawImage: jest.fn() } as any);
    jest.spyOn(canvas, 'toBlob').mockImplementation((cb) => (cb as (b: Blob | null) => void)(fakeBlob));

    component.capture();
  }

  describe('capture()', () => {
    beforeEach(async () => {
      await component.openCamera();
    });

    it('draws the current video frame, shows a preview, and closes the camera without emitting yet', () => {
      const video = (component as any).videoElementRef.nativeElement as HTMLVideoElement;
      const canvas = (component as any).canvasElementRef.nativeElement as HTMLCanvasElement;
      Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
      Object.defineProperty(video, 'videoHeight', { value: 480, configurable: true });

      const fakeBlob = new Blob(['fake'], { type: 'image/jpeg' });
      const drawImageSpy = jest.fn();
      jest.spyOn(canvas, 'getContext').mockReturnValue({ drawImage: drawImageSpy } as any);
      const toBlobSpy = jest
        .spyOn(canvas, 'toBlob')
        .mockImplementation((cb) => (cb as (b: Blob | null) => void)(fakeBlob));

      const emitted = jest.fn();
      component.photoCaptured.subscribe(emitted);

      component.capture();

      expect(drawImageSpy).toHaveBeenCalledWith(video, 0, 0, 640, 480);
      expect(toBlobSpy).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.92);
      expect(emitted).not.toHaveBeenCalled();
      expect(component.capturedImage()).toBe('blob:mock-preview-url');
      expect(component.streamActive()).toBe(false);
      expect(fakeTrack.stop).toHaveBeenCalled();
    });

    it('does nothing when the video has no dimensions yet', () => {
      const video = (component as any).videoElementRef.nativeElement as HTMLVideoElement;
      Object.defineProperty(video, 'videoWidth', { value: 0, configurable: true });
      Object.defineProperty(video, 'videoHeight', { value: 0, configurable: true });

      const emitted = jest.fn();
      component.photoCaptured.subscribe(emitted);

      component.capture();

      expect(emitted).not.toHaveBeenCalled();
      expect(component.streamActive()).toBe(true);
    });
  });

  describe('confirm()', () => {
    beforeEach(async () => {
      await component.openCamera();
      captureAFrame();
    });

    it('emits the captured file and clears the preview', () => {
      let captured: File | undefined;
      component.photoCaptured.subscribe((file) => (captured = file));

      component.confirm();

      expect(captured).toBeInstanceOf(File);
      expect(captured?.name).toBe('photo.jpg');
      expect(component.capturedImage()).toBeNull();
      expect(globalThis.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-preview-url');
    });

    it('does nothing if there is no pending capture', () => {
      component.confirm();
      const emitted = jest.fn();
      component.photoCaptured.subscribe(emitted);

      component.confirm();

      expect(emitted).not.toHaveBeenCalled();
    });
  });

  describe('retake()', () => {
    it('clears the preview and reopens the camera', async () => {
      await component.openCamera();
      captureAFrame();
      expect(component.capturedImage()).not.toBeNull();

      component.retake();
      await Promise.resolve();

      expect(component.capturedImage()).toBeNull();
      expect(globalThis.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-preview-url');
      expect(getUserMediaMock).toHaveBeenCalledTimes(2);
    });
  });

  describe('closeCamera()', () => {
    it('stops all tracks and deactivates the stream', async () => {
      await component.openCamera();

      component.closeCamera();

      expect(fakeTrack.stop).toHaveBeenCalled();
      expect(component.streamActive()).toBe(false);
    });
  });

  describe('ngOnDestroy()', () => {
    it('stops the stream if the camera was left open', async () => {
      await component.openCamera();

      component.ngOnDestroy();

      expect(fakeTrack.stop).toHaveBeenCalled();
    });

    it('is a no-op when the camera was never opened', () => {
      expect(() => component.ngOnDestroy()).not.toThrow();
    });
  });
});
