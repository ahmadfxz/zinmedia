package com.zinmedia.photoeditor.engine

import java.io.IOException

internal sealed interface SaveFileResult {

    object Success : SaveFileResult
    class Failure(internal val exception: IOException) : SaveFileResult

}