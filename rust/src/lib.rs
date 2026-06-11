mod config;
mod filters;
mod image_kernel;
mod pipeline;

use config::{Anime4KConfig, Anime4KMode, DeblurLevel, DenoiseLevel, QualityPreset};
use image_kernel::ImageKernel;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jdouble, jint, jstring};
use jni::JNIEnv;

fn process_file(input: &str, output: &str, config: &Anime4KConfig) -> Result<(), Box<dyn std::error::Error>> {
    let image = image::open(input)?;
    let mut kernel = ImageKernel::from_image(image);
    pipeline::run_pipeline(&mut kernel, config);
    kernel.save(output)?;
    Ok(())
}

fn jstring_to_string(env: &mut JNIEnv, value: JString) -> Result<String, String> {
    env.get_string(&value)
        .map(|s| s.to_string_lossy().into_owned())
        .map_err(|e| format!("JNI string error: {e}"))
}

fn string_to_jstring(env: &mut JNIEnv, value: &str) -> jstring {
    env.new_string(value)
        .expect("Can't create Java string")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_anime4k_upscaler_NativeBridge_processImage(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    output_path: JString,
    mode: JString,
    scale: jdouble,
    quality: JString,
    iterations: jint,
    pcs: jdouble,
    pgs: jdouble,
    denoise: JString,
    deblur: JString,
    line_darken: jdouble,
    line_thin: jdouble,
    clamp_highlights: jboolean,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let input_path = jstring_to_string(&mut env, input_path)?;
        let output_path = jstring_to_string(&mut env, output_path)?;
        let mode = Anime4KMode::parse(&jstring_to_string(&mut env, mode)?).ok_or("Unknown mode")?;
        let quality = QualityPreset::parse(&jstring_to_string(&mut env, quality)?).ok_or("Unknown quality")?;
        let denoise = DenoiseLevel::parse(&jstring_to_string(&mut env, denoise)?).ok_or("Unknown denoise")?;
        let deblur = DeblurLevel::parse(&jstring_to_string(&mut env, deblur)?).ok_or("Unknown deblur")?;

        let config = Anime4KConfig {
            scale,
            iterations: iterations.max(0) as u8,
            push_color_strength: pcs,
            push_gradient_strength: pgs,
            mode,
            quality,
            denoise,
            deblur,
            line_darken,
            line_thin,
            clamp_highlights: clamp_highlights != 0,
            ..Anime4KConfig::default()
        }
        .normalize_for_mode();

        process_file(&input_path, &output_path, &config).map_err(|e| e.to_string())?;
        Ok(output_path)
    })();

    match result {
        Ok(path) => string_to_jstring(&mut env, &format!("OK:{path}")),
        Err(error) => string_to_jstring(&mut env, &format!("ERR:{error}")),
    }
}
