import random
import string
import time
from fastapi import Request, Response, FastAPI
import oaas_sdk_py as oaas

# logging.basicConfig(level=logging.INFO)
app = FastAPI()


def generate_text(num):
  letters = string.ascii_lowercase
  return ''.join(random.choice(letters) for _ in range(num))


@app.post('/')
async def handle(request: Request,
                 response: Response):
  body = await request.json()
  task = oaas.parse_task_from_dict(body)
  # output_obj = body['output']
  # args = output_obj['origin'].get('args', {})
  record = task.main_obj.record
  entries = int(task.args.get('ENTRIES', '10'))
  keys = int(task.args.get('KEYS', '10'))
  values = int(task.args.get('VALUES', '10'))

  for _ in range(entries):
    record[generate_text(keys)] = generate_text(values)

  task.create_reply_header(response.headers)
  record['ts'] = round(time.time() * 1000)
  return task.create_completion(success=True, record=record)
