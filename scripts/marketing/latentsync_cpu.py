"""LatentSync 1.6 inference on CPU (its scripts/inference.py assumes CUDA). Run with the LatentSync repo as the working
directory, after the CPU patches in .github/workflows/marketing-lipsync.yml (face detector on CPU, fp32 face warping).
Called by lipsync.py for one chunk."""
import argparse, os, sys

sys.path.insert(0, os.getcwd())

import torch
from accelerate.utils import set_seed
from diffusers import AutoencoderKL, DDIMScheduler
from DeepCache import DeepCacheSDHelper
from omegaconf import OmegaConf

from latentsync.models.unet import UNet3DConditionModel
from latentsync.pipelines.lipsync_pipeline import LipsyncPipeline
from latentsync.whisper.audio2feature import Audio2Feature


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--audio", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--steps", type=int, default=20)
    ap.add_argument("--guidance", type=float, default=1.5)
    ap.add_argument("--seed", type=int, default=1247)
    a = ap.parse_args()
    torch.set_num_threads(os.cpu_count() or 4)
    config = OmegaConf.load("configs/unet/stage2_512.yaml")
    dtype = torch.float32
    audio_encoder = Audio2Feature(model_path="checkpoints/whisper/tiny.pt", device="cpu",
                                  num_frames=config.data.num_frames, audio_feat_length=config.data.audio_feat_length)
    vae = AutoencoderKL.from_pretrained("stabilityai/sd-vae-ft-mse", torch_dtype=dtype)
    vae.config.scaling_factor, vae.config.shift_factor = 0.18215, 0
    vae.enable_slicing()  # encode / decode one frame at a time: 16 frames at 512 px at once run a 7 GB runner out of memory
    unet, _ = UNet3DConditionModel.from_pretrained(OmegaConf.to_container(config.model), "checkpoints/latentsync_unet.pt",
                                                   device="cpu")
    pipe = LipsyncPipeline(vae=vae, audio_encoder=audio_encoder, unet=unet.to(dtype=dtype),
                           scheduler=DDIMScheduler.from_pretrained("configs")).to("cpu")
    helper = DeepCacheSDHelper(pipe=pipe)
    helper.set_params(cache_interval=3, cache_branch_id=0)
    helper.enable()
    set_seed(a.seed)
    pipe(video_path=a.video, audio_path=a.audio, video_out_path=a.out, num_frames=config.data.num_frames,
         num_inference_steps=a.steps, guidance_scale=a.guidance, weight_dtype=dtype, width=config.data.resolution,
         height=config.data.resolution, mask_image_path=config.data.mask_image_path, temp_dir="temp")


if __name__ == "__main__":
    main()
