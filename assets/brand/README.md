# Moekoma brand assets

This folder holds the logo, the banner, and the app icon.

## Design

The mark of Asabunko: a girl reads a manga. The open book hides her face up
to the eyes. A green morning sun is behind her, and a green tag on the book
says 漫画 (manga). Moekoma has the green in place of the red of Asabunko.

## Colours

| Name | Hex | Use |
| --- | --- | --- |
| Green | `#3DD68C` | The sun, the tag, and the star of the banner. The accent of the dark look. |
| Deep green | `#1A6B43` | The accent of the paper look, for contrast. |
| Cream | `#FBF4ED` | The lines of the girl, the book, and the word. |
| Black | `#000000` | The background of the icon. |

## Files

| File | Use |
| --- | --- |
| `logo-asabunko.png` | The logo of Asabunko. The two scripts start from it. |
| `logo-full.png` | The banner of the README, with the word "moekoma". |
| `icon-1024.png`, `icon-512.png` | The icon, the mark on black. |
| `mark.png` | The mark on black, for the empty screens. |
| `render.swift` | Makes the icons, the mark, and the launcher icons of the app. |
| `banner.swift` | Makes `logo-full.png`. |

Make the files again in this folder:

```
swift render.swift
swift banner.swift
```
