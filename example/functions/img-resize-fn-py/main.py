import logging
import os
import time
from io import BytesIO

import aiohttp
import oaas_sdk_py as oaas
import uvicorn
from PIL import Image
from fastapi import Request, FastAPI, HTTPException
from oaas_sdk_py import OaasInvocationCtx

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
IMAGE_KEY = os.getenv("IMAGE_KEY", "image")
level = logging.getLevelName(LOG_LEVEL)
logging.basicConfig(level=level)


class MemResizeHandler(oaas.Handler):
    async def handle(self, ctx: OaasInvocationCtx):
        size = ctx.args.get('size', '')
        ratio = float(ctx.args.get('ratio', '1'))
        inplace = ctx.task.output_obj is None or ctx.task.output_obj.id is None
        req_ts = int(ctx.args.get('reqts', '0'))
        record = ctx.task.main_obj.data.copy() if ctx.task.main_obj.data is not None else {}
        if req_ts != 0:
            record['reqts'] = req_ts
        start_ts = time.time()
        async with aiohttp.ClientSession() as session:
            async with await ctx.load_main_file(session, IMAGE_KEY) as resp:
                image_bytes = await resp.read()
                loading_time = time.time() - start_ts
                logging.debug(f"load data in {loading_time} s")
                with Image.open(BytesIO(image_bytes)) as img:
                    if size:
                        width, height = map(int, size.split('x'))
                        resized_image = img.resize((width, height))
                    elif ratio:
                        width, height = img.size
                        width = int(width * ratio)
                        height = int(height * ratio)
                        resized_image = img.resize((width, height))
                    byte_io = BytesIO()
                    img_format = img.format
                    resized_image.save(byte_io, format=img_format)
                    resized_image_bytes = byte_io.getvalue()

                start_ts = time.time()
                if inplace:
                    await ctx.upload_main_byte_data(session, IMAGE_KEY, resized_image_bytes)
                else:
                    await ctx.upload_byte_data(session, IMAGE_KEY, resized_image_bytes)
                uploading_time = time.time() - start_ts
                logging.debug(f"upload data in {uploading_time} s")
                record['ts'] = round(time.time() * 1000)
                record['load'] = round(loading_time * 1000)
                record['upload'] = round(uploading_time * 1000)
                record['width'] = width
                record['height'] = height
                record['format'] = img_format

        if inplace:
            ctx.task.main_obj.data = record
        else:
            ctx.task.output_obj.data = record


app = FastAPI()
router = oaas.Router()
router.register(MemResizeHandler())


@app.post('/')
async def handle(request: Request):
    body = await request.json()
    logging.debug("request %s", body)
    resp = await router.handle_task(body)
    logging.debug("completion %s", resp)
    if resp is None:
        logging.warning("No handler matched '%s'", body['funcKey'])
        raise HTTPException(status_code=404, detail="No handler matched")
    return resp


if __name__ == "__main__":
    level = logging.getLevelName("DEBUG")
    logging.basicConfig(level=level)
    uvicorn.run(app, host="0.0.0.0", port=8080)
