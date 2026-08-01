"""测试chat路由的SSE"""

import asyncio
import json
from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

router = APIRouter()

@router.post("/test/chat")
async def test_chat(request: Request):
    """测试路由级SSE"""
    print("[TEST ROUTER] Handler called")

    async def sse_gen():
        print("[TEST ROUTER] Generator started")
        yield f"event: message\ndata: {json.dumps({'delta': 'test from router'})}\n\n"
        yield f"event: done\ndata: {json.dumps({})}\n\n"
        print("[TEST ROUTER] Generator finished")

    print("[TEST ROUTER] Returning StreamingResponse")
    return StreamingResponse(sse_gen(), media_type="text/event-stream")