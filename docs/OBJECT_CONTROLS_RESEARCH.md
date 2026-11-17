# Object controls research

## Verified interaction patterns

- Microsoft Word for Android documents a touch model with three operations for shapes and objects: tap, hold, and drag to move; tap and drag a resize handle; tap, hold, and drag the rotation handle to rotate.
  Source: https://support.microsoft.com/en-us/word/word-for-android-touch-guide
- WPS Office Academy's mobile image resizing article describes selecting the image, showing eight drag handles around it, and also exposing a resize command in the image toolbar with height, width, and an aspect-ratio lock option.
  Source: https://www.wps.com/academy/how-to-resize-image-in-wps-office-in-mobileeasy-and-simple-quick-tutorials-1877498/
- WPS Docs' collaboration-mode image guide also describes control handles around the selected image, including corner handles for proportional scaling and middle handles for horizontal/vertical resizing.
  Source: https://help.wps.com/c/wps-docs-editing-collaboration/2104742007175499777-wps-docs-collaboration-mode-guide-to-inserting-images

## Implementation decisions for RemoOffice

- Use a contextual selection frame with eight resize handles and a distinct rotation handle, hidden when no image, shape, text box, SmartArt, or chart is selected.
- Support moving the selected object by dragging its body or an explicit move affordance, while preserving normal text editing inside text boxes.
- Use corner handles for proportional resizing; use edge-midpoint handles for width-only or height-only resizing.
- Keep existing toolbar resize and alignment actions, and route them to the currently selected visual object, not images only.
- Keep selection affordances out of saved document HTML; store only object content and geometric formatting.
