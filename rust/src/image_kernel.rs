use std::cmp::{max, min, PartialOrd};

pub fn clamp<T: PartialOrd>(val: T, min: T, max: T) -> T {
    if val < min {
        min
    } else if val > max {
        max
    } else {
        val
    }
}

#[inline]
pub fn extract_pixel_rgba(pixel: image::Rgba<u8>) -> (u8, u8, u8, u8) {
    (pixel[0], pixel[1], pixel[2], pixel[3])
}

// https://stackoverflow.com/a/596241/3894179
#[inline]
pub fn get_brightness(r: u8, g: u8, b: u8) -> u32 {
    (r as u32 + r as u32 + g as u32 + g as u32 + g as u32 + b as u32) / 6
}

pub fn get_largest_alpha_avg(
    cc: image::Rgba<u8>,
    lightest_color: image::Rgba<u8>,
    a: image::Rgba<u8>,
    b: image::Rgba<u8>,
    c: image::Rgba<u8>,
    strength: u16,
) -> image::Rgba<u8> {
    let new_color_r = ((cc[0] as u32 * (0xFF - strength) as u32
        + ((a[0] as u32 + b[0] as u32 + c[0] as u32) / 3) * strength as u32)
        / 0xFF) as u8;
    let new_color_g = ((cc[1] as u32 * (0xFF - strength) as u32
        + ((a[1] as u32 + b[1] as u32 + c[1] as u32) / 3) * strength as u32)
        / 0xFF) as u8;
    let new_color_b = ((cc[2] as u32 * (0xFF - strength) as u32
        + ((a[2] as u32 + b[2] as u32 + c[2] as u32) / 3) * strength as u32)
        / 0xFF) as u8;
    let new_color_a = ((cc[3] as u32 * (0xFF - strength) as u32
        + ((a[3] as u32 + b[3] as u32 + c[3] as u32) / 3) * strength as u32)
        / 0xFF) as u8;

    let new_color = image::Rgba::<u8>([new_color_r, new_color_g, new_color_b, new_color_a]);

    if new_color[3] > lightest_color[3] {
        new_color
    } else {
        lightest_color
    }
}

pub fn get_alpha_avg(
    cc: image::Rgba<u8>,
    a: image::Rgba<u8>,
    b: image::Rgba<u8>,
    c: image::Rgba<u8>,
    strength: u16,
) -> image::Rgba<u8> {
    let new_color_r = ((cc[0] as u32 * (0xFF - strength) as u32
        + ((a[0] as u32 + b[0] as u32 + c[0] as u32) / 3) * strength as u32)
        / 0xFF) as u8;
    let new_color_g = ((cc[1] as u32 * (0xFF - strength) as u32
        + ((a[1] as u32 + b[1] as u32 + c[1] as u32) / 3) * strength as u32)
        / 0xFF) as u8;
    let new_color_b = ((cc[2] as u32 * (0xFF - strength) as u32
        + ((a[2] as u32 + b[2] as u32 + c[2] as u32) / 3) * strength as u32)
        / 0xFF) as u8;
    let new_color_a = ((cc[3] as u32 * (0xFF - strength) as u32
        + ((a[3] as u32 + b[3] as u32 + c[3] as u32) / 3) * strength as u32)
        / 0xFF) as u8;

    image::Rgba::<u8>([new_color_r, new_color_g, new_color_b, new_color_a])
}

pub struct ImageKernel {
    pub image: image::ImageBuffer<image::Rgba<u8>, Vec<u8>>,
}

impl ImageKernel {
    pub fn from_image(image: image::DynamicImage) -> ImageKernel {
        ImageKernel {
            image: image.to_rgba(),
        }
    }

    pub fn width(&self) -> u32 {
        self.image.width()
    }

    pub fn height(&self) -> u32 {
        self.image.height()
    }

    pub fn scale(&mut self, width: u32, height: u32) {
        self.image = image::imageops::resize(
            &self.image,
            width,
            height,
            image::imageops::FilterType::CatmullRom,
        );
    }

    pub fn compute_luminance(&mut self) {
        for y in 0..self.image.height() {
            for x in 0..self.image.width() {
                let pixel = self.image.get_pixel_mut(x, y);
                let (r, g, b, _) = extract_pixel_rgba(*pixel);
                let brightness = get_brightness(r, g, b);
                let luminance_value = clamp(brightness, 0, 0xFF);

                pixel[0] = r;
                pixel[1] = g;
                pixel[2] = b;
                pixel[3] = luminance_value as u8;
            }
        }
    }

    pub fn compute_gradient(&mut self) {
        let sobelx = [[-1, 0, 1], [-2, 0, 2], [-1, 0, 1]];
        let sobely = [[-1, -2, -1], [0, 0, 0], [1, 2, 1]];

        let mut temp_image =
            image::DynamicImage::new_rgba8(self.image.width(), self.image.height()).to_rgba();
        for y in 1..self.image.height() - 1 {
            for x in 1..self.image.width() - 1 {
                let dx = self.image.get_pixel(x - 1, y - 1)[3] as i32 * sobelx[0][0]
                    + self.image.get_pixel(x, y - 1)[3] as i32 * sobelx[0][1]
                    + self.image.get_pixel(x + 1, y - 1)[3] as i32 * sobelx[0][2]
                    + self.image.get_pixel(x - 1, y)[3] as i32 * sobelx[1][0]
                    + self.image.get_pixel(x, y)[3] as i32 * sobelx[1][1]
                    + self.image.get_pixel(x + 1, y)[3] as i32 * sobelx[1][2]
                    + self.image.get_pixel(x - 1, y + 1)[3] as i32 * sobelx[2][0]
                    + self.image.get_pixel(x, y + 1)[3] as i32 * sobelx[2][1]
                    + self.image.get_pixel(x + 1, y + 1)[3] as i32 * sobelx[2][2];

                let dy = self.image.get_pixel(x - 1, y - 1)[3] as i32 * sobely[0][0]
                    + self.image.get_pixel(x, y - 1)[3] as i32 * sobely[0][1]
                    + self.image.get_pixel(x + 1, y - 1)[3] as i32 * sobely[0][2]
                    + self.image.get_pixel(x - 1, y)[3] as i32 * sobely[1][0]
                    + self.image.get_pixel(x, y)[3] as i32 * sobely[1][1]
                    + self.image.get_pixel(x + 1, y)[3] as i32 * sobely[1][2]
                    + self.image.get_pixel(x - 1, y + 1)[3] as i32 * sobely[2][0]
                    + self.image.get_pixel(x, y + 1)[3] as i32 * sobely[2][1]
                    + self.image.get_pixel(x + 1, y + 1)[3] as i32 * sobely[2][2];

                let derivata = (((dx * dx) + (dy * dy)) as f64).sqrt() as u32;

                let pixel = self.image.get_pixel(x, y);
                if derivata > 255 {
                    temp_image.put_pixel(
                        x,
                        y,
                        image::Rgba::<u8>([pixel[0], pixel[1], pixel[2], 0]),
                    );
                } else {
                    temp_image.put_pixel(
                        x,
                        y,
                        image::Rgba::<u8>([pixel[0], pixel[1], pixel[2], (0xFF - derivata) as u8]),
                    );
                }
            }
        }
        self.image = temp_image;
    }

    pub fn push_color(&mut self, strength: u16) {
        let mut temp_image =
            image::DynamicImage::new_rgba8(self.image.width(), self.image.height()).to_rgba();
        for y in 0..self.image.height() {
            for x in 0..self.image.width() {
                /*
                 * Kernel defination:
                 * --------------
                 * [tl] [tc] [tr]
                 * [ml] [mc] [mc]
                 * [bl] [bc] [br]
                 * --------------
                 */
                let mut x_r: i32 = 1;
                let mut x_l: i32 = -1;
                let mut y_b: i32 = 1;
                let mut y_t: i32 = -1;

                if x == 0 {
                    x_l = 0;
                } else if x == self.image.width() - 1 {
                    x_r = 0;
                }

                if y == 0 {
                    y_t = 0;
                } else if y == self.image.height() - 1 {
                    y_b = 0;
                }

                // Top column
                let tl = *self
                    .image
                    .get_pixel((x as i32 + x_l) as u32, (y as i32 + y_t) as u32);
                let tc = *self.image.get_pixel(x, (y as i32 + y_t) as u32);
                let tr = *self
                    .image
                    .get_pixel((x as i32 + x_r) as u32, (y as i32 + y_t) as u32);

                // Middle column
                let ml = *self.image.get_pixel((x as i32 + x_l) as u32, y);
                let mc = *self.image.get_pixel(x, y);
                let mr = *self.image.get_pixel((x as i32 + x_r) as u32, y);

                // Bottom column
                let bl = *self
                    .image
                    .get_pixel((x as i32 + x_l) as u32, (y as i32 + y_b) as u32);
                let bc = *self.image.get_pixel(x, (y as i32 + y_b) as u32);
                let br = *self
                    .image
                    .get_pixel((x as i32 + x_r) as u32, (y as i32 + y_b) as u32);

                let mut lightest_color = mc;

                // Kernel 0 and 4
                let mut max_dark = max(bl[3], max(bc[3], br[3]));
                let mut min_light = min(tl[3], min(tc[3], tr[3]));

                if min_light > mc[3] && min_light > max_dark {
                    lightest_color =
                        get_largest_alpha_avg(mc, lightest_color, tl, tc, tr, strength);
                } else {
                    max_dark = max(tl[3], max(tc[3], tr[3]));
                    min_light = min(br[3], min(bc[3], bl[3]));
                    if min_light > mc[3] && min_light > max_dark {
                        lightest_color =
                            get_largest_alpha_avg(mc, lightest_color, br, bc, bl, strength);
                    }
                }

                // Kernel 1 and 5
                max_dark = max(mc[3], max(ml[3], bc[3]));
                min_light = min(mr[3], min(tc[3], tr[3]));

                if min_light > max_dark {
                    lightest_color =
                        get_largest_alpha_avg(mc, lightest_color, mr, tc, tr, strength);
                } else {
                    max_dark = max(mc[3], max(mr[3], tc[3]));
                    min_light = min(bl[3], min(ml[3], bc[3]));
                    if min_light > max_dark {
                        lightest_color =
                            get_largest_alpha_avg(mc, lightest_color, bl, ml, bc, strength);
                    }
                }

                // Kernel 2 and 6
                max_dark = max(ml[3], max(tl[3], bl[3]));
                min_light = min(mr[3], min(tr[3], br[3]));

                if min_light > mc[3] && min_light > max_dark {
                    lightest_color =
                        get_largest_alpha_avg(mc, lightest_color, mr, br, tr, strength);
                } else {
                    max_dark = max(mr[3], max(tr[3], br[3]));
                    min_light = min(ml[3], min(tl[3], bl[3]));
                    if min_light > mc[3] && min_light > max_dark {
                        lightest_color =
                            get_largest_alpha_avg(mc, lightest_color, ml, tl, bl, strength);
                    }
                }

                // Kernel 3 and 7
                max_dark = max(mc[3], max(ml[3], tc[3]));
                min_light = min(mr[3], min(br[3], bc[3]));

                if min_light > max_dark {
                    lightest_color =
                        get_largest_alpha_avg(mc, lightest_color, mr, br, bc, strength);
                } else {
                    max_dark = max(mc[3], max(mr[3], bc[3]));
                    min_light = min(tc[3], min(ml[3], tl[3]));
                    if min_light > max_dark {
                        lightest_color =
                            get_largest_alpha_avg(mc, lightest_color, tc, ml, tl, strength);
                    }
                }

                temp_image.put_pixel(x, y, lightest_color);
            }
        }
        self.image = temp_image;
    }

    pub fn push_gradient(&mut self, strength: u16) {
        let mut temp_image =
            image::DynamicImage::new_rgba8(self.image.width(), self.image.height()).to_rgba();
        for y in 0..self.image.height() {
            for x in 0..self.image.width() {
                /*
                 * Kernel defination:
                 * --------------
                 * [tl] [tc] [tr]
                 * [ml] [mc] [mc]
                 * [bl] [bc] [br]
                 * --------------
                 */
                let mut x_r: i32 = 1;
                let mut x_l: i32 = -1;
                let mut y_b: i32 = 1;
                let mut y_t: i32 = -1;

                if x == 0 {
                    x_l = 0;
                } else if x == self.image.width() - 1 {
                    x_r = 0;
                }

                if y == 0 {
                    y_t = 0;
                } else if y == self.image.height() - 1 {
                    y_b = 0;
                }

                // Top column
                let tl = *self
                    .image
                    .get_pixel((x as i32 + x_l) as u32, (y as i32 + y_t) as u32);
                let tc = *self.image.get_pixel(x, (y as i32 + y_t) as u32);
                let tr = *self
                    .image
                    .get_pixel((x as i32 + x_r) as u32, (y as i32 + y_t) as u32);

                // Middle column
                let ml = *self.image.get_pixel((x as i32 + x_l) as u32, y);
                let mc = *self.image.get_pixel(x, y);
                let mr = *self.image.get_pixel((x as i32 + x_r) as u32, y);

                // Bottom column
                let bl = *self
                    .image
                    .get_pixel((x as i32 + x_l) as u32, (y as i32 + y_b) as u32);
                let bc = *self.image.get_pixel(x, (y as i32 + y_b) as u32);
                let br = *self
                    .image
                    .get_pixel((x as i32 + x_r) as u32, (y as i32 + y_b) as u32);

                let mut lightest_color = mc;

                // Kernel 0 and 4
                let mut max_dark = max(bl[3], max(bc[3], br[3]));
                let mut min_light = min(tl[3], min(tc[3], tr[3]));

                if min_light > mc[3] && min_light > max_dark {
                    lightest_color = get_alpha_avg(mc, tl, tc, tr, strength);
                } else {
                    max_dark = max(tl[3], max(tc[3], tr[3]));
                    min_light = min(br[3], min(bc[3], bl[3]));
                    if min_light > mc[3] && min_light > max_dark {
                        lightest_color = get_alpha_avg(mc, br, bc, bl, strength);
                    }
                }

                // Kernel 1 and 5
                max_dark = max(mc[3], max(ml[3], bc[3]));
                min_light = min(mr[3], min(tc[3], tr[3]));

                if min_light > max_dark {
                    lightest_color = get_alpha_avg(mc, mr, tc, tr, strength);
                } else {
                    max_dark = max(mc[3], max(mr[3], tc[3]));
                    min_light = min(bl[3], min(ml[3], bc[3]));
                    if min_light > max_dark {
                        lightest_color = get_alpha_avg(mc, bl, ml, bc, strength);
                    }
                }

                // Kernel 2 and 6
                max_dark = max(ml[3], max(tl[3], bl[3]));
                min_light = min(mr[3], min(tr[3], br[3]));

                if min_light > mc[3] && min_light > max_dark {
                    lightest_color = get_alpha_avg(mc, mr, br, tr, strength);
                } else {
                    max_dark = max(mr[3], max(tr[3], br[3]));
                    min_light = min(ml[3], min(tl[3], bl[3]));
                    if min_light > mc[3] && min_light > max_dark {
                        lightest_color = get_alpha_avg(mc, ml, tl, bl, strength);
                    }
                }

                // Kernel 3 and 7
                max_dark = max(mc[3], max(ml[3], tc[3]));
                min_light = min(mr[3], min(br[3], bc[3]));

                if min_light > max_dark {
                    lightest_color = get_alpha_avg(mc, mr, br, bc, strength);
                } else {
                    max_dark = max(mc[3], max(mr[3], bc[3]));
                    min_light = min(tc[3], min(ml[3], tl[3]));
                    if min_light > max_dark {
                        lightest_color = get_alpha_avg(mc, tc, ml, tl, strength);
                    }
                }

                lightest_color[3] = 255;
                temp_image.put_pixel(x, y, lightest_color);
            }
        }
        self.image = temp_image;
    }


    pub fn clamp_highlights(&mut self, strength: f64) {
        let strength = clamp((strength * 255.0) as i32, 0, 255) as u32;
        let mut temp_image = self.image.clone();

        for y in 0..self.image.height() {
            for x in 0..self.image.width() {
                let pixel = *self.image.get_pixel(x, y);
                let max_channel = max(pixel[0], max(pixel[1], pixel[2])) as u32;
                if max_channel > 235 {
                    let pull = ((max_channel - 235) * strength) / 255;
                    temp_image.put_pixel(
                        x,
                        y,
                        image::Rgba::<u8>([
                            pixel[0].saturating_sub(pull as u8),
                            pixel[1].saturating_sub(pull as u8),
                            pixel[2].saturating_sub(pull as u8),
                            pixel[3],
                        ]),
                    );
                }
            }
        }

        self.image = temp_image;
    }

    pub fn denoise_luma(&mut self, strength: u16) {
        if strength == 0 {
            return;
        }

        let mut temp_image = self.image.clone();
        let threshold = min(strength as i32, 255);

        for y in 0..self.image.height() {
            for x in 0..self.image.width() {
                let center = *self.image.get_pixel(x, y);
                let mut sum = 0u32;
                let mut count = 0u32;

                for yy in y.saturating_sub(1)..=min(y + 1, self.image.height() - 1) {
                    for xx in x.saturating_sub(1)..=min(x + 1, self.image.width() - 1) {
                        let p = *self.image.get_pixel(xx, yy);
                        if (p[3] as i32 - center[3] as i32).abs() <= threshold {
                            sum += p[3] as u32;
                            count += 1;
                        }
                    }
                }

                if count > 0 {
                    let mut out = center;
                    out[3] = (sum / count) as u8;
                    temp_image.put_pixel(x, y, out);
                }
            }
        }

        self.image = temp_image;
    }

    pub fn denoise_color(&mut self, strength: u16) {
        if strength == 0 {
            return;
        }

        let mut temp_image = self.image.clone();
        let threshold = min(strength as i32, 255);

        for y in 0..self.image.height() {
            for x in 0..self.image.width() {
                let center = *self.image.get_pixel(x, y);
                let mut r = 0u32;
                let mut g = 0u32;
                let mut b = 0u32;
                let mut a = 0u32;
                let mut count = 0u32;

                for yy in y.saturating_sub(1)..=min(y + 1, self.image.height() - 1) {
                    for xx in x.saturating_sub(1)..=min(x + 1, self.image.width() - 1) {
                        let p = *self.image.get_pixel(xx, yy);
                        let diff = (p[0] as i32 - center[0] as i32).abs()
                            + (p[1] as i32 - center[1] as i32).abs()
                            + (p[2] as i32 - center[2] as i32).abs();
                        if diff <= threshold * 3 {
                            r += p[0] as u32;
                            g += p[1] as u32;
                            b += p[2] as u32;
                            a += p[3] as u32;
                            count += 1;
                        }
                    }
                }

                if count > 0 {
                    temp_image.put_pixel(
                        x,
                        y,
                        image::Rgba::<u8>([
                            (r / count) as u8,
                            (g / count) as u8,
                            (b / count) as u8,
                            (a / count) as u8,
                        ]),
                    );
                }
            }
        }

        self.image = temp_image;
    }

    pub fn deblur(&mut self, amount: f64) {
        if amount <= 0.0 {
            return;
        }

        let amount = amount.max(0.0).min(2.0);
        let original = self.image.clone();
        self.denoise_color(32);
        let blurred = self.image.clone();
        let mut temp_image = original.clone();

        for y in 0..original.height() {
            for x in 0..original.width() {
                let src = *original.get_pixel(x, y);
                let blur = *blurred.get_pixel(x, y);
                let sharpen = |c: usize| -> u8 {
                    clamp(
                        (src[c] as f64 + (src[c] as f64 - blur[c] as f64) * amount).round() as i32,
                        0,
                        255,
                    ) as u8
                };
                temp_image.put_pixel(
                    x,
                    y,
                    image::Rgba::<u8>([sharpen(0), sharpen(1), sharpen(2), src[3]]),
                );
            }
        }

        self.image = temp_image;
    }

    pub fn darken_lines(&mut self, amount: f64) {
        if amount <= 0.0 {
            return;
        }

        let original = self.image.clone();
        self.compute_luminance();
        self.compute_gradient();
        let gradient = self.image.clone();
        let mut temp_image = original.clone();
        let amount = amount.max(0.0).min(1.0);

        for y in 0..original.height() {
            for x in 0..original.width() {
                let mut pixel = *original.get_pixel(x, y);
                let edge = 1.0 - (gradient.get_pixel(x, y)[3] as f64 / 255.0);
                let factor = 1.0 - edge * amount;
                pixel[0] = clamp((pixel[0] as f64 * factor).round() as i32, 0, 255) as u8;
                pixel[1] = clamp((pixel[1] as f64 * factor).round() as i32, 0, 255) as u8;
                pixel[2] = clamp((pixel[2] as f64 * factor).round() as i32, 0, 255) as u8;
                pixel[3] = 255;
                temp_image.put_pixel(x, y, pixel);
            }
        }

        self.image = temp_image;
    }

    pub fn thin_lines(&mut self, amount: f64) {
        if amount <= 0.0 {
            return;
        }

        let amount = amount.max(0.0).min(1.0);
        let original = self.image.clone();
        let mut temp_image = original.clone();

        for y in 1..original.height().saturating_sub(1) {
            for x in 1..original.width().saturating_sub(1) {
                let center = *original.get_pixel(x, y);
                let center_b = get_brightness(center[0], center[1], center[2]) as i32;
                let mut best = center;
                let mut best_b = center_b;

                for yy in y - 1..=y + 1 {
                    for xx in x - 1..=x + 1 {
                        let p = *original.get_pixel(xx, yy);
                        let pb = get_brightness(p[0], p[1], p[2]) as i32;
                        if pb > best_b {
                            best_b = pb;
                            best = p;
                        }
                    }
                }

                if best_b - center_b > 18 {
                    let mix = |c: usize| -> u8 {
                        clamp(
                            (center[c] as f64 * (1.0 - amount) + best[c] as f64 * amount).round() as i32,
                            0,
                            255,
                        ) as u8
                    };
                    temp_image.put_pixel(x, y, image::Rgba::<u8>([mix(0), mix(1), mix(2), center[3]]));
                }
            }
        }

        self.image = temp_image;
    }

    pub fn save(&self, filename: &str) -> image::ImageResult<()> {
        Ok(self.image.save(filename)?)
    }
}
