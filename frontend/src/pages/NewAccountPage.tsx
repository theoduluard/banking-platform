import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { PlusCircle } from 'lucide-react'

const schema = z.object({
  type: z.enum(['CHECKING', 'SAVINGS'], { message: 'Choisissez un type' }),
})

type FormData = z.infer<typeof schema>

export default function NewAccountPage() {
  const navigate = useNavigate()
  const userId   = getUserIdFromToken()

  const { control, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit({ type }: FormData) {
    try {
      await api.post<Account>('/api/v1/accounts', { type }, {
        headers: { 'X-User-Id': userId },
      })
      toast.success('Compte créé avec succès !')
      navigate('/dashboard')
    } catch {
      toast.error('Impossible de créer le compte.')
    }
  }

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <PlusCircle size={20} />
            Ouvrir un compte
          </CardTitle>
          <CardDescription>
            Choisissez le type de compte que vous souhaitez ouvrir.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            <div className="space-y-1">
              <Label>Type de compte</Label>
              <Controller
                name="type"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={field.onChange} value={field.value}>
                    <SelectTrigger>
                      <SelectValue placeholder="Sélectionnez…" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="CHECKING">
                        <div>
                          <p className="font-medium">Compte courant</p>
                          <p className="text-xs text-muted-foreground">Paiements du quotidien, virements</p>
                        </div>
                      </SelectItem>
                      <SelectItem value="SAVINGS">
                        <div>
                          <p className="font-medium">Compte épargne</p>
                          <p className="text-xs text-muted-foreground">Épargne et placement</p>
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
              {errors.type && <p className="text-xs text-destructive">{errors.type.message}</p>}
            </div>

            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'Création…' : 'Ouvrir le compte'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
