package com.mkotb.scheduler.routes

open class BaseResponse (
    val success: Boolean
)

open class SuccessfulResponse: BaseResponse(true)
open class ErrorResponse (
    val message: String
): BaseResponse(false)
