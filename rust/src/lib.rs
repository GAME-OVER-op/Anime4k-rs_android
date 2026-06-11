mod image_kernel;

use image_kernel::ImageKernel;
use jni::objects::{JClass, JString};
use jni::sys::{jdouble, jint, jstring};
use jni::JNIEnv;

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

fn process_classic_file(
    input: &str,
    output: &str,
    scale: f64,
    iterations: u8,
    pcs: f64,
    pgs: f64,
) -> Result<(), Box<dyn std::error::Error>> {
    let image = image::open(input)?;
    let mut kernel = ImageKernel::from_image(image);
    kernel.scale(
        (kernel.width() as f64 * scale).round().max(1.0) as u32,
        (kernel.height() as f64 * scale).round().max(1.0) as u32,
    );
    for _ in 0..iterations {
        kernel.compute_luminance();
        kernel.push_color(image_kernel::clamp((pcs * 255.0) as u16, 0, 0xFFFF));
        kernel.compute_gradient();
        kernel.push_gradient(image_kernel::clamp((pgs * 255.0) as u16, 0, 0xFFFF));
    }
    kernel.save(output)?;
    Ok(())
}

#[no_mangle]
pub extern "system" fn Java_com_anime4k_upscaler_NativeBridge_processClassicImage(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    output_path: JString,
    scale: jdouble,
    iterations: jint,
    pcs: jdouble,
    pgs: jdouble,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let input_path = jstring_to_string(&mut env, input_path)?;
        let output_path = jstring_to_string(&mut env, output_path)?;
        process_classic_file(
            &input_path,
            &output_path,
            scale,
            iterations.max(1) as u8,
            pcs,
            pgs,
        )
        .map_err(|e| e.to_string())?;
        Ok(output_path)
    })();

    match result {
        Ok(path) => string_to_jstring(&mut env, &format!("OK:{path}")),
        Err(error) => string_to_jstring(&mut env, &format!("ERR:{error}")),
    }
}
