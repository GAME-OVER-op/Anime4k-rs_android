
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Anime4KMode {
    /// Legacy Anime4K-rs pipeline: bicubic upscale + push color/gradient.
    Legacy,
    /// Mode A: optimized for clean 1080p anime.
    A,
    /// Mode B: softer restore, useful for 720p or aliased content.
    B,
    /// Mode C: upscale + denoise path for low-res or noisy content.
    C,
    /// A+A: restore again after the first upscale for stronger line recovery.
    AA,
    /// B+B: soft restore again after the first upscale.
    BB,
    /// C+A: denoise upscale followed by restore upscale.
    CA,
    /// Custom: use explicit config toggles.
    Custom,
}

impl Anime4KMode {
    pub fn parse(value: &str) -> Option<Self> {
        match value.to_ascii_lowercase().as_str() {
            "legacy" | "old" | "classic" => Some(Self::Legacy),
            "a" | "1080p" | "clean" => Some(Self::A),
            "b" | "720p" | "soft" => Some(Self::B),
            "c" | "480p" | "denoise" => Some(Self::C),
            "aa" | "a+a" => Some(Self::AA),
            "bb" | "b+b" => Some(Self::BB),
            "ca" | "c+a" => Some(Self::CA),
            "custom" => Some(Self::Custom),
            _ => None,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum QualityPreset {
    Fast,
    Balanced,
    High,
    Ultra,
}

impl QualityPreset {
    pub fn parse(value: &str) -> Option<Self> {
        match value.to_ascii_lowercase().as_str() {
            "fast" | "s" => Some(Self::Fast),
            "balanced" | "balance" | "m" => Some(Self::Balanced),
            "high" | "quality" | "l" => Some(Self::High),
            "ultra" | "max" | "vl" | "ul" => Some(Self::Ultra),
            _ => None,
        }
    }

    pub fn default_iterations(self) -> u8 {
        match self {
            Self::Fast => 1,
            Self::Balanced => 2,
            Self::High => 3,
            Self::Ultra => 4,
        }
    }

    pub fn default_denoise(self) -> DenoiseLevel {
        match self {
            Self::Fast => DenoiseLevel::Off,
            Self::Balanced => DenoiseLevel::Low,
            Self::High => DenoiseLevel::Medium,
            Self::Ultra => DenoiseLevel::High,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RestoreMode {
    Off,
    Restore,
    RestoreSoft,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DenoiseLevel {
    Off,
    Low,
    Medium,
    High,
}

impl DenoiseLevel {
    pub fn parse(value: &str) -> Option<Self> {
        match value.to_ascii_lowercase().as_str() {
            "off" | "none" | "0" => Some(Self::Off),
            "low" | "1" => Some(Self::Low),
            "medium" | "mid" | "2" => Some(Self::Medium),
            "high" | "strong" | "3" => Some(Self::High),
            _ => None,
        }
    }

    pub fn strength(self) -> u16 {
        match self {
            Self::Off => 0,
            Self::Low => 24,
            Self::Medium => 48,
            Self::High => 72,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DeblurLevel {
    Off,
    Low,
    Medium,
    High,
}

impl DeblurLevel {
    pub fn parse(value: &str) -> Option<Self> {
        match value.to_ascii_lowercase().as_str() {
            "off" | "none" | "0" => Some(Self::Off),
            "low" | "1" => Some(Self::Low),
            "medium" | "mid" | "2" => Some(Self::Medium),
            "high" | "strong" | "3" => Some(Self::High),
            _ => None,
        }
    }

    pub fn amount(self) -> f64 {
        match self {
            Self::Off => 0.0,
            Self::Low => 0.35,
            Self::Medium => 0.60,
            Self::High => 0.90,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum InterpolationMode {
    Off,
    Duplicate,
    Blend,
}

impl InterpolationMode {
    pub fn parse(value: &str) -> Option<Self> {
        match value.to_ascii_lowercase().as_str() {
            "off" | "none" | "0" => Some(Self::Off),
            "duplicate" | "dup" | "fast" => Some(Self::Duplicate),
            "blend" | "linear" | "mix" => Some(Self::Blend),
            _ => None,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub struct FrameInterpolationConfig {
    pub mode: InterpolationMode,
    pub multiplier: u8,
}

impl Default for FrameInterpolationConfig {
    fn default() -> Self {
        Self { mode: InterpolationMode::Off, multiplier: 1 }
    }
}

#[derive(Clone, Debug)]
pub struct Anime4KConfig {
    pub scale: f64,
    pub iterations: u8,
    pub push_color_strength: f64,
    pub push_gradient_strength: f64,
    pub mode: Anime4KMode,
    pub quality: QualityPreset,
    pub restore: RestoreMode,
    pub denoise: DenoiseLevel,
    pub deblur: DeblurLevel,
    pub line_darken: f64,
    pub line_thin: f64,
    pub clamp_highlights: bool,
    pub interpolation: FrameInterpolationConfig,
}

impl Default for Anime4KConfig {
    fn default() -> Self {
        Self {
            scale: 2.0,
            iterations: 1,
            push_color_strength: 0.0,
            push_gradient_strength: 1.0,
            mode: Anime4KMode::Legacy,
            quality: QualityPreset::Balanced,
            restore: RestoreMode::Off,
            denoise: DenoiseLevel::Off,
            deblur: DeblurLevel::Off,
            line_darken: 0.0,
            line_thin: 0.0,
            clamp_highlights: false,
            interpolation: FrameInterpolationConfig::default(),
        }
    }
}

impl Anime4KConfig {
    pub fn legacy(scale: f64, iterations: u8, push_color_strength: f64, push_gradient_strength: f64) -> Self {
        Self { scale, iterations, push_color_strength, push_gradient_strength, ..Self::default() }
    }

    /// Adjusts unset/neutral values to sensible defaults for Anime4K v4-inspired modes.
    pub fn normalize_for_mode(mut self) -> Self {
        if self.iterations == 0 {
            self.iterations = self.quality.default_iterations();
        }

        match self.mode {
            Anime4KMode::Legacy => self,
            Anime4KMode::A => {
                self.clamp_highlights = true;
                self.restore = RestoreMode::Restore;
                self
            }
            Anime4KMode::B => {
                self.clamp_highlights = true;
                self.restore = RestoreMode::RestoreSoft;
                self
            }
            Anime4KMode::C => {
                self.clamp_highlights = true;
                if self.denoise == DenoiseLevel::Off {
                    self.denoise = self.quality.default_denoise();
                }
                self
            }
            Anime4KMode::AA => {
                self.clamp_highlights = true;
                self.restore = RestoreMode::Restore;
                self
            }
            Anime4KMode::BB => {
                self.clamp_highlights = true;
                self.restore = RestoreMode::RestoreSoft;
                self
            }
            Anime4KMode::CA => {
                self.clamp_highlights = true;
                if self.denoise == DenoiseLevel::Off {
                    self.denoise = self.quality.default_denoise();
                }
                self.restore = RestoreMode::Restore;
                self
            }
            Anime4KMode::Custom => self,
        }
    }
}
