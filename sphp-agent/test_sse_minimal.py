"""最小化测试：验证SSE是否工作"""

import asyncio
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import json

app = FastAPI()

@app.post("/test/stream")
async def test_stream():
    """测试generator"""
    async def generator():
        print("[TEST] Generator called")
        yield f"event: message\ndata: {json.dumps({'delta': 'hello'})}\n\n"
        yield f"event: done\ndata: {json.dumps({})}\n\n"
        print("[TEST] Generator finished")

    print("[TEST] Returning StreamingResponse")
    return StreamingResponse(
        generator(),
        media_type="text/event-stream",
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=9999)
