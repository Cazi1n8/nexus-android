package br.com.nexus.assistant.extensions

import org.junit.Assert.*
import org.junit.Test

class PackAccessTest {
    @Test fun packageCannotBeRemovedOrReplacedUntilEveryNativeReaderCloses() {
        val access = PackAccess()
        val one = access.acquire(ExtensionPackId.VISION_OBJECTS)
        val two = access.acquire(ExtensionPackId.VISION_OBJECTS)
        assertThrows(ExtensionUnavailableException::class.java) { access.mutate(ExtensionPackId.VISION_OBJECTS) }
        one.close(); one.close()
        assertThrows(ExtensionUnavailableException::class.java) { access.mutate(ExtensionPackId.VISION_OBJECTS) }
        two.close()
        access.mutate(ExtensionPackId.VISION_OBJECTS).close()
    }

    @Test fun activeMutationRejectsReadersAndAnotherMutationButOtherPacksRemainIndependent() {
        val access = PackAccess()
        val mutation = access.mutate(ExtensionPackId.VISION_OCR)
        assertThrows(ExtensionUnavailableException::class.java) { access.acquire(ExtensionPackId.VISION_OCR) }
        assertThrows(ExtensionUnavailableException::class.java) { access.mutate(ExtensionPackId.VISION_OCR) }
        access.acquire(ExtensionPackId.VISION_OBJECTS).close()
        mutation.close(); mutation.close()
        access.acquire(ExtensionPackId.VISION_OCR).close()
        access.mutate(ExtensionPackId.VISION_OCR).close()
    }
}
