"""挂号工具封装（系分 §5.3）。

MCP 工具：query_departments, query_doctors, query_schedule_slots,
          create_appointment, query_appointments, cancel_appointment,
          join_waitlist, query_payment_status
对应 Java API: /api/c/v1/departments, /doctors, /appointments 等
接口路径统一由 java_api_map 契约表解析，Java 接口变化只改契约表。
"""

from app.infrastructure.java_client import call_java_api


async def query_departments(
    hospital_id: int, keyword: str | None = None, user_id: int | None = None
) -> dict:
    """查询科室列表。"""
    params = {"hospital_id": hospital_id}
    if keyword:
        params["keyword"] = keyword
    return await call_java_api(tool_name="query_departments", params=params, user_id=user_id)


async def query_doctors(
    hospital_id: int, department_id: int, date: str | None = None, user_id: int | None = None
) -> dict:
    """按科室查询医生及号源概览。"""
    params = {"hospital_id": hospital_id, "department_id": department_id}
    if date:
        params["date"] = date
    return await call_java_api(tool_name="query_doctors", params=params, user_id=user_id)


async def query_schedule_slots(
    doctor_id: int, hospital_id: int, date: str, user_id: int | None = None
) -> dict:
    """查询医生可预约时段与余量。"""
    params = {"hospital_id": hospital_id, "date": date}
    return await call_java_api(
        tool_name="query_schedule_slots",
        path_params={"doctor_id": doctor_id},
        params=params,
        user_id=user_id,
    )


async def create_appointment(
    hospital_id: int, slot_id: int, patient_id: int | None = None, user_id: int | None = None
) -> dict:
    """创建挂号锁定订单（预扣号源）。"""
    body = {"hospital_id": hospital_id, "slot_id": slot_id}
    if patient_id:
        body["patient_id"] = patient_id
    return await call_java_api(tool_name="create_appointment", body=body, user_id=user_id)


async def query_appointments(
    appointment_id: int | None = None, status: str | None = None, user_id: int | None = None
) -> dict:
    """查询挂号订单列表或详情。"""
    if appointment_id:
        return await call_java_api(
            api_name="query_appointments:detail",
            path_params={"appointment_id": appointment_id},
            user_id=user_id,
        )
    params = {}
    if status:
        params["status"] = status
    return await call_java_api(api_name="query_appointments:list", params=params, user_id=user_id)


async def cancel_appointment(appointment_id: int, user_id: int | None = None) -> dict:
    """取消挂号锁定订单。"""
    return await call_java_api(
        api_name="cancel_appointment",
        path_params={"appointment_id": appointment_id},
        user_id=user_id,
    )


async def join_waitlist(
    slot_id: int, patient_id: int | None = None, user_id: int | None = None
) -> dict:
    """号源约满时登记候补。"""
    body = {"slot_id": slot_id}
    if patient_id:
        body["patient_id"] = patient_id
    return await call_java_api(tool_name="join_waitlist", body=body, user_id=user_id)


async def query_payment_status(payment_id: int, user_id: int | None = None) -> dict:
    """查询支付单状态。"""
    return await call_java_api(
        api_name="query_payment_status",
        path_params={"payment_id": payment_id},
        user_id=user_id,
    )
