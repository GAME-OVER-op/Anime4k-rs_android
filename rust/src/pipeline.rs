
use crate::config::{Anime4KConfig, Anime4KMode, DeblurLevel, DenoiseLevel, RestoreMode};
use crate::image_kernel::{clamp, ImageKernel};

#[derive(Clone, Debug)]
pub enum Stage {
    Scale(f64),
    ClampHighlights(f64),
    Restore { soft: bool, iterations: u8, color_strength: f64, gradient_strength: f64 },
    UpscaleDenoise { scale: f64, denoise: DenoiseLevel },
    Denoise(DenoiseLevel),
    Deblur(DeblurLevel),
    DarkenLines(f64),
    ThinLines(f64),
}

pub fn build_pipeline(config: &Anime4KConfig) -> Vec<Stage> {
    let cfg = config.clone().normalize_for_mode();
    let mut stages = Vec::new();

    if cfg.clamp_highlights {
        stages.push(Stage::ClampHighlights(0.75));
    }

    match cfg.mode {
        Anime4KMode::Legacy | Anime4KMode::Custom => {
            stages.push(Stage::Scale(cfg.scale));
            if cfg.restore != RestoreMode::Off || cfg.mode == Anime4KMode::Legacy {
                stages.push(Stage::Restore {
                    soft: cfg.restore == RestoreMode::RestoreSoft,
                    iterations: cfg.iterations,
                    color_strength: cfg.push_color_strength,
                    gradient_strength: cfg.push_gradient_strength,
                });
            }
            if cfg.denoise != DenoiseLevel::Off { stages.push(Stage::Denoise(cfg.denoise)); }
            if cfg.deblur != DeblurLevel::Off { stages.push(Stage::Deblur(cfg.deblur)); }
        }
        Anime4KMode::A | Anime4KMode::B => {
            stages.push(Stage::Restore {
                soft: cfg.mode == Anime4KMode::B,
                iterations: cfg.iterations,
                color_strength: cfg.push_color_strength,
                gradient_strength: cfg.push_gradient_strength,
            });
            stages.push(Stage::Scale(cfg.scale));
        }
        Anime4KMode::C => {
            stages.push(Stage::UpscaleDenoise { scale: cfg.scale, denoise: cfg.denoise });
        }
        Anime4KMode::AA | Anime4KMode::BB => {
            let soft = cfg.mode == Anime4KMode::BB;
            let first_scale = if cfg.scale >= 4.0 { 2.0 } else { cfg.scale.sqrt().max(1.0) };
            let second_scale = cfg.scale / first_scale;
            stages.push(Stage::Restore {
                soft,
                iterations: cfg.iterations,
                color_strength: cfg.push_color_strength,
                gradient_strength: cfg.push_gradient_strength,
            });
            stages.push(Stage::Scale(first_scale));
            stages.push(Stage::Restore {
                soft,
                iterations: 1,
                color_strength: cfg.push_color_strength * 0.5,
                gradient_strength: cfg.push_gradient_strength * 0.75,
            });
            stages.push(Stage::Scale(second_scale));
        }
        Anime4KMode::CA => {
            let first_scale = if cfg.scale >= 4.0 { 2.0 } else { cfg.scale.sqrt().max(1.0) };
            let second_scale = cfg.scale / first_scale;
            stages.push(Stage::UpscaleDenoise { scale: first_scale, denoise: cfg.denoise });
            stages.push(Stage::Restore {
                soft: false,
                iterations: cfg.iterations,
                color_strength: cfg.push_color_strength,
                gradient_strength: cfg.push_gradient_strength,
            });
            stages.push(Stage::Scale(second_scale));
        }
    }

    if cfg.line_darken > 0.0 { stages.push(Stage::DarkenLines(cfg.line_darken)); }
    if cfg.line_thin > 0.0 { stages.push(Stage::ThinLines(cfg.line_thin)); }
    if cfg.deblur != DeblurLevel::Off && !matches!(cfg.mode, Anime4KMode::Legacy | Anime4KMode::Custom) {
        stages.push(Stage::Deblur(cfg.deblur));
    }

    stages
}

pub fn run_pipeline(kernel: &mut ImageKernel, config: &Anime4KConfig) {
    let stages = build_pipeline(config);
    for stage in stages {
        run_stage(kernel, &stage);
    }
}

pub fn run_stage(kernel: &mut ImageKernel, stage: &Stage) {
    match *stage {
        Stage::Scale(scale) => {
            let width = (kernel.width() as f64 * scale).round().max(1.0) as u32;
            let height = (kernel.height() as f64 * scale).round().max(1.0) as u32;
            kernel.scale(width, height);
        }
        Stage::ClampHighlights(strength) => kernel.clamp_highlights(strength),
        Stage::Restore { soft, iterations, color_strength, gradient_strength } => {
            let iters = iterations.max(1);
            for _ in 0..iters {
                kernel.compute_luminance();
                kernel.push_color(clamp((color_strength * 255.0) as u16, 0, 0xFFFF));
                if soft {
                    kernel.denoise_luma(18);
                }
                kernel.compute_gradient();
                kernel.push_gradient(clamp((gradient_strength * 255.0) as u16, 0, 0xFFFF));
            }
        }
        Stage::UpscaleDenoise { scale, denoise } => {
            if denoise != DenoiseLevel::Off { kernel.denoise_color(denoise.strength()); }
            let width = (kernel.width() as f64 * scale).round().max(1.0) as u32;
            let height = (kernel.height() as f64 * scale).round().max(1.0) as u32;
            kernel.scale(width, height);
            if denoise != DenoiseLevel::Off { kernel.denoise_color(denoise.strength() / 2); }
        }
        Stage::Denoise(level) => kernel.denoise_color(level.strength()),
        Stage::Deblur(level) => kernel.deblur(level.amount()),
        Stage::DarkenLines(amount) => kernel.darken_lines(amount),
        Stage::ThinLines(amount) => kernel.thin_lines(amount),
    }
}
